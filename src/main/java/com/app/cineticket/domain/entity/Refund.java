package com.app.cineticket.domain.entity;

import com.app.cineticket.domain.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Estorno de um pagamento (PaymentIntent) de um ingresso. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "tb_refund")
public class Refund extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Quem devolveu o dinheiro no caixa (só em venda de balcão). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;
}
