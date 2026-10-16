package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.TicketOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * O que cobrar: um ingresso avulso ou um pedido com vários ingressos.
 *
 * @param reference   "ticket-12" ou "order-5": compõe a descrição e a chave de idempotência
 * @param metadataKey "ticketId" ou "orderId": o webhook usa para achar o que foi pago
 * @param id          ID do ingresso ou do pedido
 * @param amount      valor total em reais
 */
public record Charge(String reference, String metadataKey, Long id, BigDecimal amount) {

    public static Charge of(Ticket ticket) {
        return new Charge("ticket-" + ticket.getId(), "ticketId", ticket.getId(), ticket.getValorPago());
    }

    public static Charge of(TicketOrder order) {
        return new Charge("order-" + order.getId(), "orderId", order.getId(), order.getTotal());
    }

    /** O Stripe trabalha na menor unidade da moeda (centavos). */
    public static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
