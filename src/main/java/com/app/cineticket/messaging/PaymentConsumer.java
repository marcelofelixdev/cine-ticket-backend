package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa pagamentos confirmados (webhook do Stripe ou simulador).
 * <ul>
 *   <li>Ingresso ainda PENDING e valor igual: aprova com um UPDATE condicional (idempotente).</li>
 *   <li>Mesmo pagamento recebido de novo: ignora.</li>
 *   <li>Ingresso que não pode mais ser aprovado (expirou, foi cancelado, valor divergente):
 *       o cliente pagou por algo que não vai receber, então o reembolso é pedido pelo outbox.</li>
 * </ul>
 * Erros inesperados vão para a DLQ, sem reprocessamento automático.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final TicketRepository ticketRepository;
    private final OutboxService outboxService;
    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.FILA_PAGAMENTOS)
    @Transactional
    public void onPaymentConfirmed(PaymentEventDTO event) {
        if (event == null || (event.ticketId() == null && event.orderId() == null)
                || event.valor() == null || event.paymentIntentId() == null) {
            throw new AmqpRejectAndDontRequeueException("Evento de pagamento inválido: " + event);
        }
        if (event.orderId() != null) {
            orderService.confirmPayment(event);
            return;
        }

        int updated = ticketRepository.approvePendingPayment(event.ticketId(), event.valor(), event.paymentIntentId());
        if (updated == 1) {
            log.info("[CONSUMER] Pagamento {} aprovado. Ingresso {} liberado.", event.paymentIntentId(), event.ticketId());
            return;
        }

        Ticket ticket = ticketRepository.findById(event.ticketId()).orElse(null);
        if (ticket == null) {
            // Ingresso apagado (ex.: reset diário do demo). Tentar de novo não resolve.
            log.warn("[CONSUMER] Pagamento {} para ingresso inexistente {}; ignorado.",
                    event.paymentIntentId(), event.ticketId());
            return;
        }

        if (ticket.getStatus() == TicketStatus.APPROVED && event.paymentIntentId().equals(ticket.getPaymentIntentId())) {
            log.info("[CONSUMER] Pagamento {} já processado; ignorado.", event.paymentIntentId());
            return;
        }

        log.warn("[CONSUMER] Pagamento {} recebido para o ingresso {} em estado {}: pedindo reembolso.",
                event.paymentIntentId(), ticket.getId(), ticket.getStatus());
        outboxService.refundRequested(new RefundEventDTO(ticket.getId(), event.paymentIntentId(), event.valor()));
    }
}
