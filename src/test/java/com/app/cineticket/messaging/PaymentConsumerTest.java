package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");

    @Mock private TicketRepository ticketRepository;
    @Mock private OutboxService outboxService;

    @InjectMocks private PaymentConsumer paymentConsumer;

    @Test
    void aprovaIngressoPendenteComValorEsperado() {
        when(ticketRepository.approvePendingPayment(1L, AMOUNT, "pi_1")).thenReturn(1);

        paymentConsumer.onPaymentConfirmed(PaymentEventDTO.ofTicket(1L, "pi_1", AMOUNT));

        verify(ticketRepository).approvePendingPayment(1L, AMOUNT, "pi_1");
        verifyNoInteractions(outboxService);
    }

    @Test
    void mesmoPagamentoRecebidoDeNovoEIgnorado() {
        when(ticketRepository.approvePendingPayment(1L, AMOUNT, "pi_1")).thenReturn(0);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.APPROVED, "pi_1")));

        paymentConsumer.onPaymentConfirmed(PaymentEventDTO.ofTicket(1L, "pi_1", AMOUNT));

        verify(outboxService, never()).refundRequested(any());
    }

    @Test
    void pagamentoDeIngressoExpiradoGeraReembolso() {
        when(ticketRepository.approvePendingPayment(1L, AMOUNT, "pi_1")).thenReturn(0);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.EXPIRED, null)));

        paymentConsumer.onPaymentConfirmed(PaymentEventDTO.ofTicket(1L, "pi_1", AMOUNT));

        verify(outboxService).refundRequested(new RefundEventDTO(1L, "pi_1", AMOUNT));
    }

    @Test
    void segundoPagamentoDeIngressoJaAprovadoEstornaOSegundo() {
        when(ticketRepository.approvePendingPayment(1L, AMOUNT, "pi_2")).thenReturn(0);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.APPROVED, "pi_1")));

        paymentConsumer.onPaymentConfirmed(PaymentEventDTO.ofTicket(1L, "pi_2", AMOUNT));

        verify(outboxService).refundRequested(new RefundEventDTO(1L, "pi_2", AMOUNT));
    }

    @Test
    void eventoIncompletoVaiParaADlq() {
        PaymentEventDTO event = PaymentEventDTO.ofTicket(1L, null, AMOUNT);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> paymentConsumer.onPaymentConfirmed(event));
        verifyNoInteractions(ticketRepository, outboxService);
    }

    private static Ticket ticket(TicketStatus status, String paymentIntentId) {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(status);
        ticket.setValorPago(AMOUNT);
        ticket.setPaymentIntentId(paymentIntentId);
        return ticket;
    }
}
