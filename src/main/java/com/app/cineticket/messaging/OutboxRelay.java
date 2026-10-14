package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.OutboxEvent;
import com.app.cineticket.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Publica no RabbitMQ os eventos pendentes do outbox.
 * <p>
 * Cada mensagem só é marcada como publicada depois da confirmação do broker
 * (publisher confirm). A entrega é "pelo menos uma vez": se a aplicação cair entre o
 * envio e o commit, a mensagem sai de novo, por isso os consumidores são idempotentes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${outbox.relay.batch-size:50}")
    private int batchSize = 50;

    @Value("${outbox.relay.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs = 5000;

    @Value("${outbox.retention:7d}")
    private Duration retention = Duration.ofDays(7);

    /** @return quantos eventos foram publicados neste ciclo */
    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:2000}",
            initialDelayString = "${outbox.relay.interval-ms:2000}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
                published++;
            } catch (RuntimeException e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage()));
                log.warn("[OUTBOX] Falha ao publicar o evento {} ({}), tentativa {}: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), e.getMessage());
                // Provavelmente o broker está fora: para o lote e tenta de novo no próximo ciclo,
                // preservando a ordem dos eventos.
                break;
            }
        }
        if (published > 0) {
            log.info("[OUTBOX] {} evento(s) publicado(s).", published);
        }
        return published;
    }

    @Scheduled(cron = "${outbox.cleanup-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void deleteOldPublished() {
        int deleted = repository.deletePublishedBefore(LocalDateTime.now().minus(retention));
        if (deleted > 0) {
            log.info("[OUTBOX] {} evento(s) publicado(s) antigo(s) removido(s).", deleted);
        }
    }

    private void send(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId("outbox-" + event.getId());
        properties.setType(event.getEventType());
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        rabbitTemplate.invoke(operations -> {
            operations.send(event.getExchange(), event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(confirmTimeoutMs);
            return null;
        });
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
