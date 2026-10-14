package com.app.cineticket.dto.request;

import java.math.BigDecimal;

/**
 * Pedido de reembolso de um pagamento.
 *
 * @param paymentIntentId pagamento a estornar. Vem explícito porque, num pagamento duplicado,
 *                        o estorno é do segundo pagamento, não do que aprovou o ingresso.
 *                        Pode ser nulo em ingressos pagos antes da integração com o Stripe.
 */
public record RefundEventDTO(
        Long ticketId,
        String paymentIntentId,
        BigDecimal valorEstorno
) {}
