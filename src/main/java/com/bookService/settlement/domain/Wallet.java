package com.bookService.settlement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 판매자별 정산 지갑. 원본(paymentModel wallet)은 @Version 낙관적 락 +
 * read-modify-write(addBalance)로 잔액을 갱신했으나, 여기서는 잔액 변경을
 * {@link com.bookService.settlement.repository.WalletRepository#creditBalance}의
 * 원자적 UPSERT 한 문장으로 대체해 낙관적 락 자체가 불필요하다(경합이 생기지 않는다).
 *
 * 엔티티는 조회 전용으로만 쓰이고, 잔액 변경은 항상 리포지토리의 벌크 UPDATE로 수행한다.
 */
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, unique = true)
    private Long sellerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
