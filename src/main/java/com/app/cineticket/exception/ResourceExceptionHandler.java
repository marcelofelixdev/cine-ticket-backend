package com.app.cineticket.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
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

    // Tratamento para ResponseStatusException (ex: Rate Limiter)
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<StandardError> handleResponseStatusException(org.springframework.web.server.ResponseStatusException e, HttpServletRequest request) {
        StandardError err = new StandardError(
                Instant.now(),
                e.getStatusCode().value(),
                e.getReason(),
                e.getReason(),
                request.getRequestURI()
        );
        return ResponseEntity.status(e.getStatusCode()).body(err);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<StandardError> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        StandardError err = new StandardError(
                Instant.now(), status.value(), "Não autenticado",
                "Credenciais inválidas", request.getRequestURI());
        return ResponseEntity.status(status).body(err);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<StandardError> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        StandardError err = new StandardError(
                Instant.now(), status.value(), "Conflito de dados",
                "A operação viola uma restrição de integridade", request.getRequestURI());
        return ResponseEntity.status(status).body(err);
    }

    // A ARMA SECRETA PARA O CONSOLE!
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardError> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Erro interno não tratado: {}", e.getMessage(), e);
        
        org.springframework.http.HttpStatus status = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "Erro interno no servidor",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.",
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(err);
    }
}
