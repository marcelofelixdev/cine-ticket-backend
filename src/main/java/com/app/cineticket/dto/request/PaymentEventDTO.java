package com.app.cineticket.dto.request;

import java.math.BigDecimal;

/**
 * Pagamento confirmado pelo gateway (webhook do Stripe ou simulador).
 * O consumidor aprova o ingresso se ele ainda estiver aguardando pagamento;
 * caso contrário, pede o reembolso.
 */
public record PaymentEventDTO(
        Long ticketId,
        String paymentIntentId,
        BigDecimal valor
) {}
