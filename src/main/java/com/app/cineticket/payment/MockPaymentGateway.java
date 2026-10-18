package com.app.cineticket.payment;

import com.app.cineticket.exception.InvalidPaymentMethodException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Simulador local, sem chaves nem rede. Imita os PaymentMethods de teste do Stripe:
 * IDs com "declined" são recusados, IDs com "threedsecure"/"authenticationrequired"
 * pedem 3-D Secure, IDs fora do padrão de teste {@code pm_card_...} são tratados como
 * inexistentes (como um pm_ de outra conta no Stripe) e o resto é aprovado na hora.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Charge charge, String paymentMethodId) {
        if (!paymentMethodId.startsWith("pm_card_")) {
            throw new InvalidPaymentMethodException(StripePaymentGateway.INVALID_PAYMENT_METHOD);
        }
        String method = paymentMethodId.toLowerCase(Locale.ROOT);
        if (method.contains("declined")) {
            return PaymentResult.declined(null, "Cartão recusado pelo emissor (simulação).");
        }
        if (method.contains("threedsecure") || method.contains("authenticationrequired")) {
            return PaymentResult.requiresAction("pi_mock_" + UUID.randomUUID());
        }
        return PaymentResult.succeeded("pi_mock_" + UUID.randomUUID());
    }

    @Override
    public boolean confirmsViaWebhook() {
        return false;
    }

    @Override
    public RefundResult refund(String paymentIntentId, java.math.BigDecimal amount, String reference) {
        return new RefundResult("re_mock_" + UUID.randomUUID());
    }
}
