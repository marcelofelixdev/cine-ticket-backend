package com.app.cineticket.domain.enums;

public enum TicketStatus {
    PENDING,
    APPROVED,
    CANCELLED,
    EMERGENCY_CANCELLED,
    /** Pagamento não confirmado dentro do prazo; o assento foi liberado. */
    EXPIRED
}
