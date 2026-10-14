package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** O gateway de pagamento falhou ou está indisponível. Responde 502. */
public class PaymentGatewayException extends BusinessException {

    public PaymentGatewayException(String msg, Throwable cause) {
        super(msg);
        if (cause != null) {
            initCause(cause);
        }
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_GATEWAY;
    }

    @Override
    public String getTitle() {
        return "Falha no gateway de pagamento";
    }
}
