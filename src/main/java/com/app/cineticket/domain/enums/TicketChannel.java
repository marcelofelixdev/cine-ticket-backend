package com.app.cineticket.domain.enums;

/** Onde o ingresso foi vendido. */
public enum TicketChannel {
    /** Site ou app, pago pelo gateway (Stripe). */
    ONLINE,
    /** Caixa do cinema, pago na hora. */
    BALCAO
}
