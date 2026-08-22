package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.dto.request.PaymentEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enviarParaFilaDePagamento(PaymentEventDTO evento) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_CINETICKET,
                RabbitMQConfig.ROUTING_KEY_PAGAMENTO,
                evento
        );
        System.out.println("[PRODUCER] Mensagem enviada para o RabbitMQ! Ingresso ID: " + evento.ticketId());
    }
}
