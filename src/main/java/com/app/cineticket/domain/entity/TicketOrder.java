package com.app.cineticket.domain.entity;

import com.app.cineticket.domain.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido da compra online (tb_order): os lugares de uma sessão reservados juntos e pagos
 * numa cobrança só. Chama-se TicketOrder para não confundir com jakarta.persistence.Order.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "tb_order")
public class TicketOrder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Prazo do pagamento; o mesmo de cada ingresso do pedido. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** PaymentIntent que pagou o pedido inteiro. */
    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @OneToMany(mappedBy = "ticketOrder")
    @OrderBy("id")
    private List<Ticket> tickets = new ArrayList<>();

    public boolean isOwnedBy(User candidate) {
        return user != null && candidate != null && user.getId().equals(candidate.getId());
    }
}
