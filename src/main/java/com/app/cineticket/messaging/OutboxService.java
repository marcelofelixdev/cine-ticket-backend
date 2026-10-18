package com.app.cineticket.messaging;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.entity.OutboxEvent;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Registra eventos no outbox. Exige uma transação aberta ({@link Propagation#MANDATORY}):
 * o evento só existe se a regra de negócio que o gerou for commitada, e vice-versa.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final String TICKET = "Ticket";
    private static final String ORDER = "Order";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentConfirmed(PaymentEventDTO event) {
        boolean order = event.orderId() != null;
        enqueue(order ? ORDER : TICKET, order ? event.orderId() : event.ticketId(), "PaymentConfirmed",
                RabbitMQConfig.ROUTING_KEY_PAGAMENTO, event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundRequested(RefundEventDTO event) {
        enqueue(TICKET, event.ticketId(), "RefundRequested", RabbitMQConfig.ROUTING_KEY_REEMBOLSO, event);
    }

    private void enqueue(String aggregateType, Object aggregateId, String eventType,
                         String routingKey, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(String.valueOf(aggregateId));
        event.setEventType(eventType);
        event.setExchange(RabbitMQConfig.EXCHANGE_CINETICKET);
        event.setRoutingKey(routingKey);
        event.setPayload(toJson(payload));
        event.setCreatedAt(LocalDateTime.now(AppTimeZone.ZONE));
        repository.save(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o evento do outbox", e);
        }
    }
}
