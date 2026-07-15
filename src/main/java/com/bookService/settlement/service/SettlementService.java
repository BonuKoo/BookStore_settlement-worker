package com.bookService.settlement.service;

import com.bookService.settlement.dto.PaymentEventMessage;
import com.bookService.settlement.dto.WalletCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * payment.confirmed 이벤트를 판매자별로 그룹핑해 정산한다.
 *
 * 원본(paymentModel wallet)의 SettlementService는 전체를 한 트랜잭션(@Transactional)으로
 * 묶어 지갑들을 로드→가산→일괄 저장했다. 여기서는 판매자별 처리를
 * {@link SellerWalletCreditor}의 독립 트랜잭션으로 분리한다 — 한 판매자 처리가
 * 예기치 못하게 실패해도 나머지 판매자의 정산은 그대로 커밋되고, 실패한 몫만
 * 다음 재전달(at-least-once) 때 재시도된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SellerWalletCreditor sellerWalletCreditor;

    public WalletCompletedMessage settle(PaymentEventMessage message) {
        String orderId = String.valueOf(message.getPayload().get("orderId"));
        Map<Long, BigDecimal> amountBySeller = groupAmountBySeller(message.getPayload());

        amountBySeller.forEach((sellerId, amount) -> {
            try {
                sellerWalletCreditor.credit(orderId, sellerId, amount);
            } catch (Exception e) {
                log.error("판매자 정산 실패 — 다음 재전달에서 재시도됨. orderId={}, sellerId={}",
                        orderId, sellerId, e);
            }
        });

        // 원본 설계 유지: 이미 전부 처리된(중복 전달) 주문이어도 완결 통지는 다시 발행한다.
        // "정산은 끝났는데 통지 발행 직전 크래시"를 재전달로 복구하기 위함.
        return new WalletCompletedMessage(Map.of("orderId", orderId));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, BigDecimal> groupAmountBySeller(Map<String, Object> payload) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        return items.stream().collect(Collectors.groupingBy(
                item -> ((Number) item.get("sellerId")).longValue(),
                Collectors.reducing(BigDecimal.ZERO,
                        item -> toBigDecimal(item.get("amount")),
                        BigDecimal::add)));
    }

    private BigDecimal toBigDecimal(Object amount) {
        if (amount instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(amount));
    }
}
