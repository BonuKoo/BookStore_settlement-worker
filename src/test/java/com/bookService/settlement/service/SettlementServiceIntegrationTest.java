package com.bookService.settlement.service;

import com.bookService.settlement.dto.PaymentEventMessage;
import com.bookService.settlement.dto.WalletCompletedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SettlementService의 오케스트레이션 계약 검증: payment.confirmed 페이로드를
 * 판매자별로 그룹핑해 각자 정산하고, 결과와 무관하게 항상 완결 통지 메시지를
 * 반환하는지 확인한다. RabbitMQ 발행은 이 클래스의 책임이 아니라(WalletCompletedPublisher가
 * 별도 담당) 브로커 없이도 실행된다.
 */
@SpringBootTest
class SettlementServiceIntegrationTest {

    private static final String ORDER_PREFIX = "settle-svc-test-";

    @Autowired private SettlementService settlementService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long sellerA;
    private Long sellerB;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE order_id LIKE ?", ORDER_PREFIX + "%");
        if (sellerA != null) jdbcTemplate.update("DELETE FROM wallets WHERE seller_id = ?", sellerA);
        if (sellerB != null) jdbcTemplate.update("DELETE FROM wallets WHERE seller_id = ?", sellerB);
    }

    private BigDecimal balanceOf(Long sellerId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE seller_id = ?", BigDecimal.class, sellerId);
    }

    private PaymentEventMessage successMessage(String orderId, List<Map<String, Object>> items) {
        PaymentEventMessage message = new PaymentEventMessage();
        message.setMessageType("PAYMENT_CONFIRMATION_SUCCESS");
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("orderId", orderId);
        payload.put("items", items);
        message.setPayload(payload);
        return message;
    }

    @Test
    @DisplayName("판매자 2명 주문 → 각자 몫만큼 정산되고 완결 통지에 orderId가 담긴다")
    void settle_groupsAndCreditsMultipleSellers() {
        sellerA = 910_000_001L;
        sellerB = 910_000_002L;
        String orderId = ORDER_PREFIX + UUID.randomUUID();

        PaymentEventMessage message = successMessage(orderId, List.of(
                Map.of("sellerId", sellerA, "amount", 12000, "productId", "isbn-1", "quantity", 1),
                Map.of("sellerId", sellerB, "amount", 8000, "productId", "isbn-2", "quantity", 1)
        ));

        WalletCompletedMessage completed = settlementService.settle(message);

        assertThat(balanceOf(sellerA)).isEqualByComparingTo("12000");
        assertThat(balanceOf(sellerB)).isEqualByComparingTo("8000");
        assertThat(completed.getPayload().get("orderId")).isEqualTo(orderId);
    }

    @Test
    @DisplayName("한 판매자에게 항목 2개 → 같은 판매자 몫은 합산되어 한 번에 정산된다")
    void settle_multipleItemsSameSeller_sumsAmount() {
        sellerA = 910_000_003L;
        String orderId = ORDER_PREFIX + UUID.randomUUID();

        PaymentEventMessage message = successMessage(orderId, List.of(
                Map.of("sellerId", sellerA, "amount", 5000, "productId", "isbn-1", "quantity", 1),
                Map.of("sellerId", sellerA, "amount", 3000, "productId", "isbn-2", "quantity", 1)
        ));

        settlementService.settle(message);

        assertThat(balanceOf(sellerA)).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("items가 비어 있어도 크래시 없이 완결 통지는 반환된다")
    void settle_emptyItems_stillReturnsCompletedMessage() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        PaymentEventMessage message = successMessage(orderId, List.of());

        WalletCompletedMessage completed = settlementService.settle(message);

        assertThat(completed.getPayload().get("orderId")).isEqualTo(orderId);
    }

    @Test
    @DisplayName("중복 전달(재처리)이어도 잔액은 그대로고 완결 통지는 다시 반환된다 — 원본의 재통지 설계 유지")
    void settle_duplicateMessage_doesNotDoubleCredit_butStillNotifies() {
        sellerA = 910_000_004L;
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        PaymentEventMessage message = successMessage(orderId, List.of(
                Map.of("sellerId", sellerA, "amount", 7000, "productId", "isbn-1", "quantity", 1)
        ));

        settlementService.settle(message);
        WalletCompletedMessage secondAttempt = settlementService.settle(message); // 재전달 시뮬레이션

        assertThat(balanceOf(sellerA)).isEqualByComparingTo("7000"); // 14000이 아니라 7000
        assertThat(secondAttempt.getPayload().get("orderId")).isEqualTo(orderId); // 그래도 통지는 됨
    }
}
