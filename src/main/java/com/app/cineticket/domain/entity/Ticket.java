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

    /** Dono do ingresso. Vazio em venda de balcão para cliente sem conta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false)
    private TicketType ticketType;

    /** Prazo para o pagamento de um ingresso PENDING. Depois dele, o ingresso expira. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Pedido da compra online que reservou este ingresso junto com outros (null no avulso e no balcão). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private TicketOrder ticketOrder;

    /** PaymentIntent do Stripe (ou do simulador) que pagou este ingresso. */
    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    /** Número da carteira de estudante (meia-entrada de estudante), conferido na entrada. */
    @Column(name = "documento_meia", length = 30)
    private String documentoMeia;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private com.app.cineticket.domain.enums.TicketChannel canal = com.app.cineticket.domain.enums.TicketChannel.ONLINE;

    /** Só em venda de balcão. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 10)
    private com.app.cineticket.domain.enums.FormaPagamento formaPagamento;

    /** Funcionário ou gerente que vendeu no balcão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendido_por")
    private User vendidoPor;

    /** E-mail informado no balcão (opcional). */
    @Column(name = "cliente_email", length = 150)
    private String clienteEmail;

    /** Ingresso sem dono (balcão, cliente sem conta) não pertence a ninguém. */
    public boolean isOwnedBy(User candidate) {
        return user != null && candidate != null && user.getId().equals(candidate.getId());
    }
}
