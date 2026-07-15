package com.bookService.settlement.repository;

import com.bookService.settlement.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * 판매자 지갑 잔액을 원자적으로 가산한다. 지갑이 없으면(첫 정산) 새로 만들고,
     * 있으면 잔액에 더한다 — MySQL UPSERT라 read-modify-write 경쟁 자체가 없다.
     * (원본의 @Version 낙관적 락 + 재시도 대신 이 한 문장이 동시성을 책임진다)
     */
    @Modifying
    @Query(value = "INSERT INTO wallets (seller_id, balance, created_at, updated_at) "
            + "VALUES (:sellerId, :amount, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance), updated_at = NOW()",
            nativeQuery = true)
    void creditBalance(@Param("sellerId") Long sellerId, @Param("amount") BigDecimal amount);
}
