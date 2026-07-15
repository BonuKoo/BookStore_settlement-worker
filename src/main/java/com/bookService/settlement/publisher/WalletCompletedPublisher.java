package com.bookService.settlement.publisher;

import com.bookService.settlement.config.RabbitMqConfig;
import com.bookService.settlement.dto.WalletCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * M2 범위에서는 outbox 없이 직접 발행한다 (재고 차감 컨슈머와 같은 신뢰성 수준).
 * manual ack + publisher confirms 배선은 Phase 4에서 이 정산 큐를 대상으로 추가된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCompletedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(WalletCompletedMessage message) {
        log.info("정산 완결 통지 발행: orderId={}", message.getPayload().get("orderId"));
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PAYMENT_EXCHANGE,
                RabbitMqConfig.SETTLEMENT_WALLET_COMPLETED_ROUTING_KEY,
                message);
    }
}
