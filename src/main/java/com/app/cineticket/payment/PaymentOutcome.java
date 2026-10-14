package com.app.cineticket.payment;

/** Resultado imediato da tentativa de cobrança no gateway. */
public enum PaymentOutcome {
    /** Cobrança aprovada. */
    SUCCEEDED,
    /** Aceita, mas ainda em processamento: a confirmação chega depois pelo webhook. */
    PROCESSING,
    /** Recusada pelo emissor (saldo, cartão bloqueado...). */
    DECLINED,
    /** Exige autenticação adicional do portador (3-D Secure). */
    REQUIRES_ACTION
}
