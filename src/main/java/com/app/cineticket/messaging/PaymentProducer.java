package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.dto.request.PaymentEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProducer {

    private final RabbitTemplate rabbitTemplate;

    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void enviarParaFilaDePagamento(PaymentEventDTO evento) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_CINETICKET,
                RabbitMQConfig.ROUTING_KEY_PAGAMENTO,
                evento
        );
        log.info("[PRODUCER] Mensagem enviada para o RabbitMQ apxs COMMIT! Ingresso ID: {}", evento.ticketId());
    }
}
