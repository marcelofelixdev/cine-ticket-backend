package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.OrderStatus;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Pedido da compra online com os ingressos dele. */
public record OrderResponseDTO(
        Long id,
        OrderStatus status,
        Long sessionId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        BigDecimal total,
        /** Prazo para pagar; depois dele os lugares voltam a ficar livres. */
        LocalDateTime expiresAt,
        List<Ingresso> ingressos
) {
    public record Ingresso(Long ticketId, String assento, TicketType tipo, BigDecimal valor, TicketStatus status) {
    }
}
