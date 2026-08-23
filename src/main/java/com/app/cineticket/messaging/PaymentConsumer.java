package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final TicketRepository ticketRepository;

    @RabbitListener(queues = RabbitMQConfig.FILA_PAGAMENTOS)
    public void processarPagamento(PaymentEventDTO evento) {
        log.info("[CONSUMER] Iniciando cobranca do ingresso: {}", evento.ticketId());

        Ticket ticket = ticketRepository.findById(evento.ticketId())
                .orElseThrow(() -> new org.springframework.amqp.AmqpRejectAndDontRequeueException("Ingresso inexistente"));

        if (ticket.getStatus() != TicketStatus.PENDING) {
            log.info("[CONSUMER] Pagamento ja processado para o ingresso: {} (Status atual: {})", evento.ticketId(), ticket.getStatus());
            return;
        }

        try {
            Thread.sleep(3000);

            ticket.setStatus(TicketStatus.APPROVED);
            ticketRepository.save(ticket);

            log.info("[CONSUMER] Pagamento APROVADO! Ingresso {} liberado para uso.", evento.ticketId());
        } catch (InterruptedException e) {
            log.error("Falha na simulacao de tempo.");
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Erro interno no processamento", e);
        } catch (Exception e) {
            log.error("Erro ao processar pagamento", e);
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Erro ao processar pagamento", e);
        }
    }

}
