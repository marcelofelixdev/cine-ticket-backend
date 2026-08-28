package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "payments.mock.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final TicketRepository ticketRepository;

    @RabbitListener(queues = RabbitMQConfig.FILA_PAGAMENTOS)
    @Transactional
    public void processarPagamento(PaymentEventDTO evento) {
        if (evento == null) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Evento de pagamento ausente");
        }
        log.info("[CONSUMER] Iniciando cobranca do ingresso: {}", evento.ticketId());

        try {
            if (evento.ticketId() == null || evento.valor() == null) {
                throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Evento de pagamento inválido");
            }
            int updated = ticketRepository.approvePendingPayment(evento.ticketId(), evento.valor());
            if (updated == 0) {
                log.info("[CONSUMER] Pagamento ignorado por estado/valor divergente. Ingresso: {}", evento.ticketId());
                return;
            }
            log.info("[CONSUMER] Pagamento APROVADO! Ingresso {} liberado para uso.", evento.ticketId());
        } catch (Exception e) {
            if (e instanceof org.springframework.amqp.AmqpRejectAndDontRequeueException reject) {
                throw reject;
            }
            log.error("Erro ao processar pagamento", e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Erro ao processar pagamento", e);
        }
    }

}
