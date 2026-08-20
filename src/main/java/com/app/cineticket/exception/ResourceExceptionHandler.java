package com.app.cineticket.exception;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ResourceExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandardError> handleBusinessException(BusinessException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "Violação de Regra de Negócio",
                e.getMessage(),
                request.getRequestURI()
        );
    return ResponseEntity.status(status).body(err);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<StandardError> handleValidationException(
            org.springframework.web.bind.MethodArgumentNotValidException e,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        String mensagensDeErro = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .reduce("", (a, b) -> a + " | " + b);

        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "Erro de Validação de Dados",
                mensagensDeErro,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(err);
    }

    // A ARMA SECRETA PARA O CONSOLE!
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardError> handleGenericException(Exception e, HttpServletRequest request) {
        // ISSO VAI PINTAR O SEU CONSOLE DO INTELLIJ DE VERMELHO COM O ERRO REAL!
        e.printStackTrace(); 
        
        org.springframework.http.HttpStatus status = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "ERRO INTERNO GRAVE",
                e.getMessage() != null ? e.getMessage() : e.toString(),
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(err);
    }
}
