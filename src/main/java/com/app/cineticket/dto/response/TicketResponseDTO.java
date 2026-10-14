package com.app.cineticket.dto.response;

public record TicketResponseDTO (
    Long id,
    String status,
    String filmeTitulo,
    String salaNome,
    String cadeiraAssento,
    java.math.BigDecimal valorPago,
    com.app.cineticket.domain.enums.TicketType ticketType,
    java.time.LocalDateTime expiresAt,
    Long sessionId,
    java.time.LocalDateTime horarioSessao,
    Long seatId,
    /** Status do reembolso do pagamento deste ingresso (PENDING, REFUNDED, FAILED...), ou null. */
    String reembolso
){}
