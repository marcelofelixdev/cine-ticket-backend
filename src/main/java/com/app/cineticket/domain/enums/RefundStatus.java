package com.app.cineticket.domain.enums;

public enum RefundStatus {
    /** Registrado, ainda não estornado (ou aguardando nova tentativa). */
    PENDING,
    /** Estornado no gateway. */
    REFUNDED,
    /** O gateway falhou; a mensagem foi para a DLQ de reembolsos depois das tentativas. */
    FAILED,
    /** Não há pagamento a estornar (ingresso pago antes da integração com o gateway). */
    NOT_APPLICABLE
}
