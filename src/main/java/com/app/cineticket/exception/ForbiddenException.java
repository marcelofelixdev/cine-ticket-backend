package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** O usuário está autenticado, mas o recurso pertence a outra pessoa. Responde 403. */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String getTitle() {
        return "Acesso negado";
    }
}
