package com.bookService.settlement.listener;

import com.bookService.settlement.config.RabbitMqConfig;
import com.bookService.settlement.dto.PaymentEventMessage;
import com.bookService.settlement.dto.WalletCompletedMessage;
import com.bookService.settlement.publisher.WalletCompletedPublisher;
import com.bookService.settlement.service.SettlementService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementListener {

    private final SettlementService settlementService;
    private final WalletCompletedPublisher walletCompletedPublisher;

    @RabbitListener(queues = RabbitMqConfig.WALLET_SETTLEMENT_QUEUE)
    public void onPaymentConfirmed(PaymentEventMessage message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        log.info("정산 대상 결제 확정 이벤트 수신: orderId={}", message.getPayload().get("orderId"));

        try {
            WalletCompletedMessage completed = settlementService.settle(message);
            walletCompletedPublisher.publish(completed);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("정산 처리 실패 — DLQ 적재: orderId={}", message.getPayload().get("orderId"), e);
            channel.basicNack(tag, false, false);
        }
    }
}
