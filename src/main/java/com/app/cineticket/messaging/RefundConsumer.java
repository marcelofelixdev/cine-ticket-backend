package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome os pedidos de reembolso (cancelamento pelo cliente, cancelamento emergencial,
 * pagamento que chegou para um ingresso que não podia mais ser aprovado).
 * <p>
 * Falha no gateway: a exceção faz o listener tentar de novo com intervalo crescente
 * ({@code spring.rabbitmq.listener.simple.retry.*}); esgotadas as tentativas, a mensagem
 * vai para a DLQ de reembolsos e o registro fica FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundConsumer {

    private final RefundService refundService;

    @RabbitListener(queues = RabbitMQConfig.FILA_REEMBOLSOS)
    public void onRefundRequested(RefundEventDTO event) {
        if (event == null || event.ticketId() == null) {
            throw new AmqpRejectAndDontRequeueException("Pedido de reembolso inválido: " + event);
        }
        RefundStatus status = refundService.process(event);
        if (status == RefundStatus.FAILED) {
            throw new RefundFailedException("Estorno do pagamento " + event.paymentIntentId() + " falhou");
        }
    }

    /** Falha temporária do gateway: dispara uma nova tentativa do listener. */
    static class RefundFailedException extends RuntimeException {
        RefundFailedException(String message) {
            super(message);
        }
    }
}
