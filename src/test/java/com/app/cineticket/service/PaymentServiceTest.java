package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.PaymentDeclinedException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.payment.PaymentGateway;
import com.app.cineticket.payment.PaymentResult;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String CARD = "pm_card_visa";

    @Mock private TicketRepository ticketRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private OutboxService outboxService;
    @Mock private TicketMapper ticketMapper;

    @InjectMocks private PaymentService paymentService;

    private User owner;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "paymentsEnabled", true);
        owner = new User();
        owner.setId(7L);
        ticket = new Ticket();
        ticket.setId(30L);
        ticket.setUser(owner);
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setValorPago(new BigDecimal("40.00"));
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    }

    @Test
    void simuladorAprovadoGravaOPagamentoEConfirmaPeloOutbox() {
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));
        when(paymentGateway.charge(ticket, CARD)).thenReturn(PaymentResult.succeeded("pi_mock_1"));
        when(paymentGateway.confirmsViaWebhook()).thenReturn(false);

        paymentService.pay(30L, CARD, owner);

        assertEquals("pi_mock_1", ticket.getPaymentIntentId());
        assertEquals(TicketStatus.PENDING, ticket.getStatus());
        verify(outboxService).paymentConfirmed(PaymentEventDTO.ofTicket(30L, "pi_mock_1", new BigDecimal("40.00")));
    }

    @Test
    void stripeAprovadoAguardaOWebhook() {
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));
        when(paymentGateway.charge(ticket, CARD)).thenReturn(PaymentResult.succeeded("pi_123"));
        when(paymentGateway.confirmsViaWebhook()).thenReturn(true);

        paymentService.pay(30L, CARD, owner);

        assertEquals("pi_123", ticket.getPaymentIntentId());
        verifyNoInteractions(outboxService);
    }

    @Test
    void cartaoRecusadoMantemAReservaSemPagamento() {
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));
        when(paymentGateway.charge(ticket, "pm_card_chargeDeclined"))
                .thenReturn(PaymentResult.declined(null, "Cartão recusado."));

        assertThrows(PaymentDeclinedException.class,
                () -> paymentService.pay(30L, "pm_card_chargeDeclined", owner));

        assertNull(ticket.getPaymentIntentId());
        assertEquals(TicketStatus.PENDING, ticket.getStatus());
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void cartaoQueExige3dsERecusado() {
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));
        when(paymentGateway.charge(ticket, CARD)).thenReturn(PaymentResult.requiresAction("pi_3ds"));

        assertThrows(PaymentDeclinedException.class, () -> paymentService.pay(30L, CARD, owner));
        assertNull(ticket.getPaymentIntentId());
    }

    @Test
    void outroUsuarioNaoPagaOIngresso() {
        User other = new User();
        other.setId(99L);
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> paymentService.pay(30L, CARD, other));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void pagamentoJaEnviadoNaoCobraDeNovo() {
        ticket.setPaymentIntentId("pi_anterior");
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> paymentService.pay(30L, CARD, owner));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void reservaComPrazoVencidoNaoPodeSerPaga() {
        ticket.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> paymentService.pay(30L, CARD, owner));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void ingressoQueNaoEstaPendenteNaoPodeSerPago() {
        ticket.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> paymentService.pay(30L, CARD, owner));
        verifyNoInteractions(paymentGateway);
    }
}
