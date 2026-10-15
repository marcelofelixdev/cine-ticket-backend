package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** Requisição incompleta ou incoerente com quem a faz (ex.: falta um parâmetro). Responde 400. */
public class BadRequestException extends BusinessException {

    public BadRequestException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getTitle() {
        return "Requisição inválida";
    }
}
