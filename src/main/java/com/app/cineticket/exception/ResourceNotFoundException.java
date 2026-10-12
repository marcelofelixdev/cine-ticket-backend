package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** O recurso solicitado não existe. Responde 404. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String getTitle() {
        return "Recurso não encontrado";
    }
}
