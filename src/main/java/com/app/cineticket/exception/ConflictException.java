package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** A operação conflita com o estado atual do recurso (duplicidade, assento ocupado...). Responde 409. */
public class ConflictException extends BusinessException {

    public ConflictException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String getTitle() {
        return "Conflito";
    }
}
