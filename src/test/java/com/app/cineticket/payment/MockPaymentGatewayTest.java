package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.exception.InvalidPaymentMethodException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O simulador imita os PaymentMethods de teste do Stripe. */
class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();
    private final Ticket ticket = new Ticket();

    @Test
    void cartaoDeTesteAprovado() {
        PaymentResult result = gateway.charge(ticket, "pm_card_visa");

        assertEquals(PaymentOutcome.SUCCEEDED, result.outcome());
        assertTrue(result.paymentIntentId().startsWith("pi_mock_"));
    }

    @Test
    void cartaoDeTesteRecusado() {
        assertEquals(PaymentOutcome.DECLINED, gateway.charge(ticket, "pm_card_chargeDeclined").outcome());
    }

    @Test
    void cartaoQueExige3ds() {
        assertEquals(PaymentOutcome.REQUIRES_ACTION, gateway.charge(ticket, "pm_card_authenticationRequired").outcome());
    }

    @Test
    void metodoDeOutraContaEInvalido() {
        InvalidPaymentMethodException e = assertThrows(InvalidPaymentMethodException.class,
                () -> gateway.charge(ticket, "pm_1QxOutraContaQualquer"));

        assertEquals("Método de pagamento inválido", e.getTitle());
    }
}
