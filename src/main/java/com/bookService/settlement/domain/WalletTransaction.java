package com.bookService.settlement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 판매자 정산 처리의 멱등 가드 + 감사 기록. 원본(paymentModel)은 wallet_id FK +
 * nullable idempotency_key로 조회 후 판단하는 방식이었으나, 여기서는
 * UNIQUE(order_id, seller_id)를 걸어 "이 주문의 이 판매자 몫은 한 번만 정산된다"를
 * DB가 직접 보장하게 한다 (재고 차감 컨슈머와 같은 원리).
 *
 * wallet_id FK를 두지 않고 seller_id를 직접 저장하는 이유: 신규 판매자의 첫 정산은
 * wallets 행이 아직 없을 수 있는데, 멱등 가드(이 테이블 insert)가 잔액 반영보다
 * 먼저 성립해야 하므로 wallets 행의 존재 여부에 의존하지 않게 분리했다.
 */
@Entity
@Table(
        name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_tx_order_seller", columnNames = {"order_id", "seller_id"})
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 255)
    private String orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionType type;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
