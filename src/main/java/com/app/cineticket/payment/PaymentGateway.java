package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;

import java.math.BigDecimal;

/**
 * Cobrança de um ingresso ou de um pedido. Implementações: {@link StripePaymentGateway}
 * ({@code payments.provider=stripe}) e {@link MockPaymentGateway} ({@code payments.provider=mock}).
 */
public interface PaymentGateway {

    PaymentResult charge(Charge charge, String paymentMethodId);

    /** Cobrança de um ingresso avulso (POST /tickets/{id}/payment). */
    default PaymentResult charge(Ticket ticket, String paymentMethodId) {
        return charge(Charge.of(ticket), paymentMethodId);
    }

    /**
     * {@code true} quando a aprovação definitiva chega pelo webhook do gateway.
     * {@code false} quando o próprio gateway já confirma na hora (simulador).
     */
    boolean confirmsViaWebhook();

    /**
     * Estorna {@code amount} de um pagamento: o valor de um ingresso, que pode ser só uma parte
     * de um pedido pago de uma vez. Idempotente pela {@code reference}: repetir não estorna de novo.
     *
     * @throws com.app.cineticket.exception.PaymentGatewayException se o gateway falhar
     */
    RefundResult refund(String paymentIntentId, BigDecimal amount, String reference);
}
