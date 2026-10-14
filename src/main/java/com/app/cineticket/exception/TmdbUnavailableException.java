package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** O TMDB falhou ou está fora do ar. Responde 502. */
public class TmdbUnavailableException extends BusinessException {

    public TmdbUnavailableException(String msg, Throwable cause) {
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
        return "Serviço de filmes indisponível";
    }
}
