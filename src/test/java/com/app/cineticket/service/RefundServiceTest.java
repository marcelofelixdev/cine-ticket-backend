package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Refund;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.exception.PaymentGatewayException;
import com.app.cineticket.payment.PaymentGateway;
import com.app.cineticket.payment.RefundResult;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("40.00");

    @Mock private RefundRepository refundRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private PaymentGateway paymentGateway;

    @InjectMocks private RefundService refundService;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setId(5L);
        ticket.setValorPago(AMOUNT);
    }

    @Test
    void estornaNoGatewayEGravaComoReembolsado() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(refundRepository.findByPaymentIntentId("pi_1")).thenReturn(Optional.empty());
        when(paymentGateway.refund("pi_1")).thenReturn(new RefundResult("re_1"));

        RefundStatus status = refundService.process(new RefundEventDTO(5L, "pi_1", AMOUNT));

        assertEquals(RefundStatus.REFUNDED, status);
        Refund saved = captureSaved();
        assertEquals("re_1", saved.getGatewayRefundId());
        assertEquals(1, saved.getAttempts());
        assertNotNull(saved.getRefundedAt());
    }

    @Test
    void mensagemRepetidaNaoEstornaDeNovo() {
        Refund refunded = new Refund();
        refunded.setStatus(RefundStatus.REFUNDED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(refundRepository.findByPaymentIntentId("pi_1")).thenReturn(Optional.of(refunded));

        RefundStatus status = refundService.process(new RefundEventDTO(5L, "pi_1", AMOUNT));

        assertEquals(RefundStatus.REFUNDED, status);
        verifyNoInteractions(paymentGateway);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void falhaDoGatewayFicaRegistradaComoFailed() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(refundRepository.findByPaymentIntentId("pi_1")).thenReturn(Optional.empty());
        when(paymentGateway.refund("pi_1")).thenThrow(new PaymentGatewayException("Stripe fora do ar", null));

        RefundStatus status = refundService.process(new RefundEventDTO(5L, "pi_1", AMOUNT));

        assertEquals(RefundStatus.FAILED, status);
        Refund saved = captureSaved();
        assertEquals("Stripe fora do ar", saved.getLastError());
        assertEquals(1, saved.getAttempts());
    }

    @Test
    void pagamentoDoSimuladorEEstornadoSemChamarOGateway() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(refundRepository.findByPaymentIntentId("pi_mock_1")).thenReturn(Optional.empty());

        RefundStatus status = refundService.process(new RefundEventDTO(5L, "pi_mock_1", AMOUNT));

        assertEquals(RefundStatus.REFUNDED, status);
        assertTrue(captureSaved().getGatewayRefundId().startsWith("re_mock_"));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void ingressoSemPagamentoNoGatewayNaoTemOQueEstornar() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(refundRepository.existsByTicketIdAndPaymentIntentIdIsNull(5L)).thenReturn(false);

        RefundStatus status = refundService.process(new RefundEventDTO(5L, null, AMOUNT));

        assertEquals(RefundStatus.NOT_APPLICABLE, status);
        assertEquals(RefundStatus.NOT_APPLICABLE, captureSaved().getStatus());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void ingressoApagadoDescartaOPedido() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.empty());

        RefundStatus status = refundService.process(new RefundEventDTO(5L, "pi_1", AMOUNT));

        assertEquals(RefundStatus.NOT_APPLICABLE, status);
        verifyNoInteractions(paymentGateway, refundRepository);
    }

    private Refund captureSaved() {
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        return captor.getValue();
    }
}
