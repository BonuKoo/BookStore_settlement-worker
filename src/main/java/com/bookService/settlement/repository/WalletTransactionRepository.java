package com.bookService.settlement.repository;

import com.bookService.settlement.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /** 흔한 중복 전달을 예외 없이 빠르게 걸러내는 사전 확인 (재고 차감 컨슈머와 같은 패턴). */
    boolean existsByOrderIdAndSellerId(String orderId, Long sellerId);
}
