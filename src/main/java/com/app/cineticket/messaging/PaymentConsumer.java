package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final TicketRepository ticketRepository;

    @RabbitListener(queues = RabbitMQConfig.FILA_PAGAMENTOS)
    public void processarPagamento(PaymentEventDTO evento) {
    System.out.println("[CONSUMER] Iniciando cobrança do ingresso: " + evento.ticketId() + " no cartão " + evento.cartaoToken());

    try {
        Thread.sleep(3000);

        Ticket ticket = ticketRepository.findById(evento.ticketId())
                .orElseThrow(() -> new RuntimeException("Ingresso inexistente"));

        ticket.setStatus(TicketStatus.APPROVED);
        ticketRepository.save(ticket);

        System.out.println("[CONSUMER] Pagamento APROVADO! Ingresso " + evento.ticketId() + " liberado para uso.");
    } catch (InterruptedException e) {
        System.out.println("Falha na simulação de tempo.");
    }
    }

}
