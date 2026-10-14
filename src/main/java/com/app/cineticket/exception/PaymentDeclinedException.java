package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** A cobrança foi recusada. O ingresso continua reservado até expirar. Responde 422. */
public class PaymentDeclinedException extends BusinessException {

    public PaymentDeclinedException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String getTitle() {
        return "Pagamento recusado";
    }
}
