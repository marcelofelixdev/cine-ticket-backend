package com.app.cineticket.domain.entity;

import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "tb_ticket")
public class Ticket extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", nullable = false)
    private TicketStatus status;

    @Column(name = "valor_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false)
    private TicketType ticketType;

    /** Prazo para o pagamento de um ingresso PENDING. Depois dele, o ingresso expira. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** PaymentIntent do Stripe (ou do simulador) que pagou este ingresso. */
    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    /** Quando o ingresso foi usado na entrada da sala (null = ainda não usado). */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** Funcionário que validou a entrada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    /** Estornos dos pagamentos deste ingresso (o mais recente primeiro). */
    @OneToMany(mappedBy = "ticket")
    @OrderBy("id DESC")
    private java.util.List<Refund> refunds = new java.util.ArrayList<>();
}
