package com.app.cineticket.messaging;

import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private PaymentConsumer paymentConsumer;

    @Test
    void deveAprovarSomenteComEstadoEValorEsperados() {
        var amount = new BigDecimal("25.00");
        when(ticketRepository.approvePendingPayment(1L, amount)).thenReturn(1);

        paymentConsumer.processarPagamento(new PaymentEventDTO(1L, "token-local", amount));

        verify(ticketRepository).approvePendingPayment(1L, amount);
    }

    @Test
    void deveTratarTransicaoJaProcessadaComoIdempotente() {
        var amount = new BigDecimal("25.00");
        when(ticketRepository.approvePendingPayment(1L, amount)).thenReturn(0);

        paymentConsumer.processarPagamento(new PaymentEventDTO(1L, "token-local", amount));

        verify(ticketRepository).approvePendingPayment(1L, amount);
    }
}
