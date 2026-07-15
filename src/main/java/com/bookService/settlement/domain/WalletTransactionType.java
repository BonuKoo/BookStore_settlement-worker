package com.bookService.settlement.domain;

public enum WalletTransactionType {
    /** M2 범위: 결제 확정에 따른 판매자 지갑 입금 */
    CREDIT,
    /** 향후 환불 등에 대비해 남겨둔 값. M2에서는 사용하지 않는다. */
    DEBIT
}
