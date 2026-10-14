package com.app.cineticket.metrics;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.OutboxEventRepository;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.TicketRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas de negócio para o Prometheus, lidas do banco a cada 30 s (e não a cada scrape):
 * <ul>
 *   <li>{@code cineticket_tickets{status,type}}: ingressos por status e tipo;</li>
 *   <li>{@code cineticket_tickets_used}: ingressos já usados na entrada;</li>
 *   <li>{@code cineticket_refunds{status}}: reembolsos por status;</li>
 *   <li>{@code cineticket_outbox_pending} e {@code cineticket_outbox_oldest_pending_seconds}:
 *       eventos que ainda não chegaram ao RabbitMQ e há quanto tempo o mais antigo espera.</li>
 * </ul>
 * Vir do banco (e não de contadores em memória) faz os números sobreviverem a reinícios
 * e baterem com a realidade mesmo com mais de uma instância.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.metrics.business.enabled", havingValue = "true", matchIfMissing = true)
public class BusinessMetrics {

    private final TicketRepository ticketRepository;
    private final RefundRepository refundRepository;
    private final OutboxEventRepository outboxRepository;

    private final Map<TicketStatus, Map<TicketType, AtomicLong>> tickets = new EnumMap<>(TicketStatus.class);
    private final Map<RefundStatus, AtomicLong> refunds = new EnumMap<>(RefundStatus.class);
    private final AtomicLong ticketsUsed = new AtomicLong();
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxOldestPendingSeconds = new AtomicLong();

    public BusinessMetrics(MeterRegistry registry,
                           TicketRepository ticketRepository,
                           RefundRepository refundRepository,
                           OutboxEventRepository outboxRepository) {
        this.ticketRepository = ticketRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;

        // Todas as combinações nascem com zero: os painéis não ficam "sem dados".
        for (TicketStatus status : TicketStatus.values()) {
            Map<TicketType, AtomicLong> byType = new EnumMap<>(TicketType.class);
            for (TicketType type : TicketType.values()) {
                AtomicLong value = new AtomicLong();
                byType.put(type, value);
                Gauge.builder("cineticket.tickets", value, AtomicLong::get)
                        .description("Ingressos por status e tipo")
                        .tags(Tags.of("status", status.name(), "type", type.name()))
                        .register(registry);
            }
            tickets.put(status, byType);
        }
        for (RefundStatus status : RefundStatus.values()) {
            AtomicLong value = new AtomicLong();
            refunds.put(status, value);
            Gauge.builder("cineticket.refunds", value, AtomicLong::get)
                    .description("Reembolsos por status")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("cineticket.tickets.used", ticketsUsed, AtomicLong::get)
                .description("Ingressos já validados na entrada")
                .register(registry);
        Gauge.builder("cineticket.outbox.pending", outboxPending, AtomicLong::get)
                .description("Eventos do outbox ainda não publicados no RabbitMQ")
                .register(registry);
        Gauge.builder("cineticket.outbox.oldest.pending", outboxOldestPendingSeconds, AtomicLong::get)
                .description("Idade do evento pendente mais antigo do outbox")
                .baseUnit("seconds")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.metrics.business.refresh-interval-ms:30000}", initialDelay = 5_000)
    @Transactional(readOnly = true)
    public void refresh() {
        tickets.values().forEach(byType -> byType.values().forEach(value -> value.set(0)));
        for (Object[] row : ticketRepository.countGroupedByStatusAndType()) {
            tickets.get((TicketStatus) row[0]).get((TicketType) row[1]).set((Long) row[2]);
        }

        refunds.values().forEach(value -> value.set(0));
        for (Object[] row : refundRepository.countGroupedByStatus()) {
            refunds.get((RefundStatus) row[0]).set((Long) row[1]);
        }

        ticketsUsed.set(ticketRepository.countByUsedAtIsNotNull());
        outboxPending.set(outboxRepository.countByPublishedAtIsNull());
        LocalDateTime oldest = outboxRepository.findOldestPendingCreatedAt();
        outboxOldestPendingSeconds.set(oldest == null ? 0
                : Math.max(0, Duration.between(oldest, LocalDateTime.now(AppTimeZone.ZONE)).toSeconds()));
    }
}
