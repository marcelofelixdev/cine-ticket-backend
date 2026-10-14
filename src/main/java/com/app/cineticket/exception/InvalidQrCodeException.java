package com.app.cineticket.exception;

import org.springframework.http.HttpStatus;

/** QR Code com formato inválido ou assinatura que não confere. Responde 400. */
public class InvalidQrCodeException extends BusinessException {

    public InvalidQrCodeException(String msg) {
        super(msg);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getTitle() {
        return "QR Code inválido";
    }
}
