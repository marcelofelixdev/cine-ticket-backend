package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;

/**
 * Cobrança de um ingresso. Implementações: {@link StripePaymentGateway}
 * ({@code payments.provider=stripe}) e {@link MockPaymentGateway} ({@code payments.provider=mock}).
 */
public interface PaymentGateway {

    PaymentResult charge(Ticket ticket, String paymentMethodId);

    /**
     * {@code true} quando a aprovação definitiva chega pelo webhook do gateway.
     * {@code false} quando o próprio gateway já confirma na hora (simulador).
     */
    boolean confirmsViaWebhook();

    /**
     * Estorna integralmente um pagamento. Idempotente: chamar de novo para o mesmo
     * PaymentIntent não gera um segundo estorno.
     *
     * @throws com.app.cineticket.exception.PaymentGatewayException se o gateway falhar
     */
    RefundResult refund(String paymentIntentId);
}
