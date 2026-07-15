package com.bookService.settlement.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매자 정산의 동시성 계약 검증. 실제 MySQL에서 스레드별 독립 트랜잭션으로 실행해
 * "UNIQUE(order_id, seller_id) 멱등 가드 + 원자 UPSERT"가 실제 경쟁 상황에서
 * 지켜지는지 본다 (core-spa 재고 차감 컨슈머 테스트와 같은 방법론).
 */
@SpringBootTest
class SellerWalletCreditorIntegrationTest {

    private static final String ORDER_PREFIX = "wallet-test-order-";

    @Autowired private SellerWalletCreditor sellerWalletCreditor;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long sellerId;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE order_id LIKE ?", ORDER_PREFIX + "%");
        if (sellerId != null) {
            jdbcTemplate.update("DELETE FROM wallets WHERE seller_id = ?", sellerId);
        }
    }

    private long newSellerId() {
        // AUTO_INCREMENT PK와 겹치지 않도록 넓은 범위의 난수를 판매자 ID로 쓴다.
        sellerId = 900_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L);
        return sellerId;
    }

    private BigDecimal currentBalance(Long sellerId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM wallets WHERE seller_id = ?", BigDecimal.class, sellerId);
    }

    @Test
    @DisplayName("첫 정산: 지갑이 없던 판매자에게 정확한 잔액으로 지갑이 생성된다")
    void credit_firstTime_createsWalletWithCorrectBalance() {
        long seller = newSellerId();
        String orderId = ORDER_PREFIX + UUID.randomUUID();

        sellerWalletCreditor.credit(orderId, seller, new BigDecimal("12000.00"));

        assertThat(currentBalance(seller)).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("누적: 서로 다른 주문 2건이 같은 판매자에게 정산되면 잔액이 합산된다")
    void credit_differentOrders_accumulatesBalance() {
        long seller = newSellerId();

        sellerWalletCreditor.credit(ORDER_PREFIX + UUID.randomUUID(), seller, new BigDecimal("5000"));
        sellerWalletCreditor.credit(ORDER_PREFIX + UUID.randomUUID(), seller, new BigDecimal("3000"));

        assertThat(currentBalance(seller)).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("멱등성: 같은 (주문, 판매자) 몫을 두 번 처리해도 잔액은 한 번만 반영된다")
    void credit_duplicateOrderSeller_creditsOnlyOnce() {
        long seller = newSellerId();
        String orderId = ORDER_PREFIX + UUID.randomUUID();

        sellerWalletCreditor.credit(orderId, seller, new BigDecimal("10000"));
        sellerWalletCreditor.credit(orderId, seller, new BigDecimal("10000")); // 재전달 시뮬레이션

        assertThat(currentBalance(seller)).isEqualByComparingTo("10000"); // 20000이 아니라 10000
    }

    @Test
    @DisplayName("동시성/멱등: 같은 (주문,판매자) 몫이 20번 동시 전달돼도 잔액은 정확히 1회만 반영된다")
    void credit_concurrentDuplicateDelivery_creditsExactlyOnce() throws InterruptedException {
        long seller = newSellerId();
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        int threads = 20;

        runConcurrently(threads, i ->
                sellerWalletCreditor.credit(orderId, seller, new BigDecimal("1000")));

        assertThat(currentBalance(seller)).isEqualByComparingTo("1000"); // 20000이었다면 중복 반영
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_transactions WHERE order_id = ? AND seller_id = ?",
                Integer.class, orderId, seller);
        assertThat(txCount).isEqualTo(1);
    }

    @Test
    @DisplayName("동시성/lost update: 서로 다른 주문 20건이 같은 판매자에게 동시 정산돼도 합계가 정확하다")
    void credit_concurrentDifferentOrders_noLostUpdate() throws InterruptedException {
        long seller = newSellerId();
        int threads = 20;

        runConcurrently(threads, i ->
                sellerWalletCreditor.credit(ORDER_PREFIX + "c-" + i + "-" + UUID.randomUUID(), seller,
                        new BigDecimal("1000")));

        // 읽고-더하고-쓰기(원본의 @Version 방식이 재시도 없이 실패)였다면 갱신 유실로 20000보다 작게 남는다
        assertThat(currentBalance(seller)).isEqualByComparingTo("20000");
    }

    private void runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(idx);
                } catch (Exception ignored) {
                    // 성공/실패 집계는 task 안에서 수행
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }
}
