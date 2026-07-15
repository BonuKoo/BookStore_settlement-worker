package com.bookService.settlement.listener;

import com.bookService.settlement.config.RabbitMqConfig;
import com.bookService.settlement.dto.PaymentEventMessage;
import com.bookService.settlement.dto.WalletCompletedMessage;
import com.bookService.settlement.publisher.WalletCompletedPublisher;
import com.bookService.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementListener {

    private final SettlementService settlementService;
    private final WalletCompletedPublisher walletCompletedPublisher;

    @RabbitListener(queues = RabbitMqConfig.WALLET_SETTLEMENT_QUEUE)
    public void onPaymentConfirmed(PaymentEventMessage message) {
        log.info("정산 대상 결제 확정 이벤트 수신: orderId={}", message.getPayload().get("orderId"));

        WalletCompletedMessage completed = settlementService.settle(message);
        walletCompletedPublisher.publish(completed);
    }
}
