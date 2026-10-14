package com.app.cineticket.dto.request;

import java.math.BigDecimal;

/**
 * Pedido de cobrança de um ingresso. Não carrega o token do cartão: o evento fica
 * gravado no outbox (banco) e na fila, e dado de pagamento não deve ficar em nenhum dos dois.
 */
public record PaymentEventDTO(
        Long ticketId,
        BigDecimal valor
) {}
