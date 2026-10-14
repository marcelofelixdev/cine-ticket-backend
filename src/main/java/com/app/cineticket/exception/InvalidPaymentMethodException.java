package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/**
 * O paymentMethodId não existe na conta do gateway (por exemplo, foi criado em outra
 * conta ou sandbox do Stripe). É erro de quem chama, não do gateway. Responde 422.
 */
public class InvalidPaymentMethodException extends BusinessException {

    public InvalidPaymentMethodException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String getTitle() {
        return "Método de pagamento inválido";
    }
}
