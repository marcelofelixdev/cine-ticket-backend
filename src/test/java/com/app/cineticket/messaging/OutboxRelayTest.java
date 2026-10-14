package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.OutboxEvent;
import com.app.cineticket.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private OutboxRelay relay;

    @Test
    void publicaOLoteEMarcaComoPublicado() {
        OutboxEvent first = event(1L);
        OutboxEvent second = event(2L);
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(first, second));

        int published = relay.publishPending();

        assertEquals(2, published);
        assertNotNull(first.getPublishedAt());
        assertNotNull(second.getPublishedAt());
        verify(rabbitTemplate, times(2)).invoke(any());
    }

    @Test
    void falhaNoBrokerMantemOEventoPendenteEInterrompeOLote() {
        OutboxEvent first = event(1L);
        OutboxEvent second = event(2L);
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(first, second));
        when(rabbitTemplate.invoke(any()))
                .thenThrow(new AmqpConnectException(new ConnectException("broker fora do ar")));

        int published = relay.publishPending();

        assertEquals(0, published);
        assertNull(first.getPublishedAt());
        assertEquals(1, first.getAttempts());
        assertNotNull(first.getLastError());
        // O segundo nem é tentado: a ordem dos eventos é preservada para o próximo ciclo.
        assertEquals(0, second.getAttempts());
        verify(rabbitTemplate, times(1)).invoke(any());
    }

    @Test
    void eventoComFalhaAnteriorEPublicadoNaProximaTentativa() {
        OutboxEvent event = event(1L);
        event.setAttempts(3);
        event.setLastError("broker fora do ar");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(event));

        relay.publishPending();

        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        assertEquals(3, event.getAttempts());
    }

    private static OutboxEvent event(Long id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateType("Ticket");
        event.setAggregateId(String.valueOf(id));
        event.setEventType("RefundRequested");
        event.setExchange("cineticket.direct.change");
        event.setRoutingKey("reembolso.routingKey");
        event.setPayload("{\"ticketId\":" + id + ",\"valorEstorno\":10.00}");
        return event;
    }
}
