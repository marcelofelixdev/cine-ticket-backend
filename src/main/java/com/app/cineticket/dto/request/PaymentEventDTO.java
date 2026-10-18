package com.app.cineticket.dto.request;

import java.math.BigDecimal;

/**
 * Pagamento confirmado pelo gateway (webhook do Stripe ou simulador): de um ingresso avulso
 * ({@code ticketId}) ou de um pedido ({@code orderId}). O consumidor aprova se ainda estiver
 * aguardando pagamento; caso contrário, pede o reembolso.
 */
public record PaymentEventDTO(
        Long ticketId,
        Long orderId,
        String paymentIntentId,
        BigDecimal valor
) {
    /** Pagamento de um ingresso avulso. */
    public static PaymentEventDTO ofTicket(Long ticketId, String paymentIntentId, BigDecimal valor) {
        return new PaymentEventDTO(ticketId, null, paymentIntentId, valor);
    }

    /** Pagamento de um pedido. */
    public static PaymentEventDTO ofOrder(Long orderId, String paymentIntentId, BigDecimal valor) {
        return new PaymentEventDTO(null, orderId, paymentIntentId, valor);
    }
}
