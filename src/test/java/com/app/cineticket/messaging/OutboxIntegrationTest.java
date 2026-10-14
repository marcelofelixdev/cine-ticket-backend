package com.app.cineticket.messaging;

import com.app.cineticket.config.RabbitMQConfig;
import com.app.cineticket.domain.entity.OutboxEvent;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.OutboxEventRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox contra PostgreSQL e RabbitMQ reais: o evento acompanha a transação de negócio
 * e o relay o entrega na fila com confirmação do broker.
 */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OutboxService outboxService;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;

    @Test
    void rollbackDaTransacaoDescartaOEvento() {
        long ticketId = 910_001L;

        transactionTemplate.executeWithoutResult(status -> {
            outboxService.refundRequested(new RefundEventDTO(ticketId, "pi_test_rollback", new BigDecimal("10.00")));
            status.setRollbackOnly();
        });

        assertTrue(eventsFor(ticketId).isEmpty());
    }

    @Test
    void registrarEventoForaDeTransacaoEProibido() {
        RefundEventDTO event = new RefundEventDTO(910_002L, "pi_test_notx", new BigDecimal("10.00"));

        assertThrows(IllegalTransactionStateException.class, () -> outboxService.refundRequested(event));
    }

    @Test
    void eventoCommitadoEPublicadoNaFilaComConfirmacao() throws InterruptedException {
        long ticketId = 910_003L;
        // A fila real de reembolsos tem consumidor; uma fila temporária ligada à mesma
        // routing key recebe uma cópia para o teste conferir o que foi publicado.
        String probe = amqpAdmin.declareQueue(new AnonymousQueue());
        amqpAdmin.declareBinding(BindingBuilder.bind(new org.springframework.amqp.core.Queue(probe))
                .to(new DirectExchange(RabbitMQConfig.EXCHANGE_CINETICKET))
                .with(RabbitMQConfig.ROUTING_KEY_REEMBOLSO));

        transactionTemplate.executeWithoutResult(status ->
                outboxService.refundRequested(new RefundEventDTO(ticketId, "pi_test_publish", new BigDecimal("25.00"))));

        outboxRelay.publishPending();
        OutboxEvent published = waitUntilPublished(ticketId);
        assertNotNull(published.getPublishedAt());

        Message message = receive(probe, "outbox-" + published.getId());
        amqpAdmin.deleteQueue(probe);
        assertNotNull(message, "a mensagem deveria ter sido roteada para a fila de reembolsos");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"ticketId\":" + ticketId), body);
        assertEquals("RefundRequested", message.getMessageProperties().getType());
    }

    private List<OutboxEvent> eventsFor(long ticketId) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(String.valueOf(ticketId)))
                .toList();
    }

    /** O relay agendado também roda no teste; espera quem chegar primeiro publicar. */
    private OutboxEvent waitUntilPublished(long ticketId) {
        return await("evento do ingresso " + ticketId + " publicado")
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> eventsFor(ticketId), events -> events.size() == 1 && events.get(0).getPublishedAt() != null)
                .get(0);
    }

    private Message receive(String queue, String messageId) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return null;
    }
}
