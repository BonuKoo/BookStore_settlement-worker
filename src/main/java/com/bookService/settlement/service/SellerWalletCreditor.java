package com.bookService.settlement.service;

import com.bookService.settlement.domain.WalletTransaction;
import com.bookService.settlement.domain.WalletTransactionType;
import com.bookService.settlement.repository.WalletRepository;
import com.bookService.settlement.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 판매자 한 명분의 정산을 독립 트랜잭션으로 처리한다. 별도 빈으로 분리한 이유는
 * 판매자별 신용이 서로 독립적이어서(재고 차감과 달리 "주문 단위 all-or-nothing"이
 * 필요 없다) 한 판매자 처리가 실패해도 다른 판매자의 이미 커밋된 정산을 되돌릴
 * 필요가 없기 때문이다 — 실패한 판매자만 다음 재전달 때 재시도된다.
 *
 * 동시성 처리 2계층 (재고 차감과 같은 원리, "부족" 개념이 없어 all-or-nothing 불필요):
 *  1) 멱등성 — wallet_transactions(order_id, seller_id) UNIQUE에 saveAndFlush를 먼저
 *     실행. 같은 주문의 같은 판매자 몫이 중복 전달되면 제약 위반으로 즉시 실패.
 *  2) Lost update 차단 — creditBalance는 INSERT ... ON DUPLICATE KEY UPDATE
 *     balance = balance + ? 원자 연산이라 읽고-더하는 경쟁 자체가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerWalletCreditor {

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public void credit(String orderId, Long sellerId, BigDecimal amount) {
        if (walletTransactionRepository.existsByOrderIdAndSellerId(orderId, sellerId)) {
            log.info("이미 정산된 판매자 몫 — 중복 전달 skip: orderId={}, sellerId={}", orderId, sellerId);
            return;
        }

        try {
            walletTransactionRepository.saveAndFlush(WalletTransaction.builder()
                    .orderId(orderId)
                    .sellerId(sellerId)
                    .amount(amount)
                    .type(WalletTransactionType.CREDIT)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("이미 정산된 판매자 몫 — 동시 중복 전달 skip: orderId={}, sellerId={}", orderId, sellerId);
            return;
        }

        walletRepository.creditBalance(sellerId, amount);
        log.info("판매자 지갑 정산 완료: orderId={}, sellerId={}, amount={}", orderId, sellerId, amount);
    }
}
