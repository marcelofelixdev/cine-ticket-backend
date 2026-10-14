package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** Webhook sem assinatura válida do gateway: possivelmente forjado. Responde 400. */
public class InvalidWebhookSignatureException extends BusinessException {

    public InvalidWebhookSignatureException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getTitle() {
        return "Assinatura do webhook inválida";
    }
}
