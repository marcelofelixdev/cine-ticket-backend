package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/**
 * Violação de regra de negócio. Responde 422 por padrão.
 * <p>
 * As subclasses definem o status HTTP e o título adequados
 * (404, 403, 409...), e o {@link ResourceExceptionHandler} trata todas de forma única.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String msg) {
        super(msg);
    }

    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public String getTitle() {
        return "Regra de negócio violada";
    }
}
