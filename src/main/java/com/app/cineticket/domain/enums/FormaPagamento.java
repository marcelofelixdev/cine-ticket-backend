package com.app.cineticket.domain.enums;

/** Forma de pagamento no caixa do cinema (não passa pelo Stripe). */
public enum FormaPagamento {
    DINHEIRO,
    CREDITO,
    DEBITO,
    PIX
}
