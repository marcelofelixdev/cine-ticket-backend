package com.app.cineticket.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Evento de webhook já processado: o mesmo ID recebido de novo é ignorado. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_processed_webhook_event")
public class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
