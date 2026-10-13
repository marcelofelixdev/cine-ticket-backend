package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** Credencial ausente, inválida ou expirada (ex.: refresh token). Responde 401. */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String getTitle() {
        return "Não autenticado";
    }
}
