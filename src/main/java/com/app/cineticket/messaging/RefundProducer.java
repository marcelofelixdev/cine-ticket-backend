package com.app.cineticket.messaging;

import com.app.cineticket.dto.request.RefundEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundProducer {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefundEvent(RefundEventDTO event) {
        log.info("Enviando evento de REEMBOLSO para o RabbitMQ: Ticket {}", event.ticketId());
        rabbitTemplate.convertAndSend(com.app.cineticket.config.RabbitMQConfig.EXCHANGE_CINETICKET, com.app.cineticket.config.RabbitMQConfig.ROUTING_KEY_REEMBOLSO, event);
    }
}
