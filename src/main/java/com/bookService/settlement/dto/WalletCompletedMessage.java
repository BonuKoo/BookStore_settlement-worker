package com.bookService.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 정산 완결 통지. core-spa(M4, 아직 미구현)가 구독해 is_wallet_updated 갱신 →
 * completeIfDone()에 반영할 예정이다.
 *
 * 원본(paymentModel wallet)의 설계를 그대로 따른다: 이미 처리된(중복 전달) 주문이어도
 * 이 메시지는 다시 발행한다 — "정산은 끝났는데 완결 통지 발행 직전에 크래시"난 경우를
 * 재전달(at-least-once)로 복구하기 위함이다. core-spa 쪽 반영은 멱등해야 한다(M4 책임).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletCompletedMessage {
    private Map<String, Object> payload;
}
