package com.app.cineticket.payment;

/**
 * @param paymentIntentId ID do PaymentIntent no gateway (pode ser nulo quando a recusa
 *                        acontece antes de o PaymentIntent existir)
 * @param message         motivo legível em caso de recusa
 */
public record PaymentResult(PaymentOutcome outcome, String paymentIntentId, String message) {

    public static PaymentResult succeeded(String paymentIntentId) {
        return new PaymentResult(PaymentOutcome.SUCCEEDED, paymentIntentId, null);
    }

    public static PaymentResult processing(String paymentIntentId) {
        return new PaymentResult(PaymentOutcome.PROCESSING, paymentIntentId, null);
    }

    public static PaymentResult declined(String paymentIntentId, String message) {
        return new PaymentResult(PaymentOutcome.DECLINED, paymentIntentId, message);
    }

    public static PaymentResult requiresAction(String paymentIntentId) {
        return new PaymentResult(PaymentOutcome.REQUIRES_ACTION, paymentIntentId, null);
    }
}
