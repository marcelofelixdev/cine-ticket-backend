package com.app.cineticket.domain.enums;

public enum TicketStatus {
    PENDING,
    APPROVED,
    CANCELLED,
    EMERGENCY_CANCELLED,
    /** Pagamento não confirmado dentro do prazo; o assento foi liberado. */
    EXPIRED;

    /** Status que ocupam o assento: o mesmo critério do índice anti-overbooking (idx_ticket_no_overbooking). */
    public static final java.util.List<TicketStatus> OCCUPYING_SEAT = java.util.List.of(PENDING, APPROVED);
}
