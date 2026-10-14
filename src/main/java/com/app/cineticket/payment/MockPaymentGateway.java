package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Simulador local, sem chaves nem rede. Imita os PaymentMethods de teste do Stripe:
 * IDs com "declined" são recusados, IDs com "threedsecure"/"authenticationrequired"
 * pedem 3-D Secure e o resto é aprovado na hora.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Ticket ticket, String paymentMethodId) {
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
}
