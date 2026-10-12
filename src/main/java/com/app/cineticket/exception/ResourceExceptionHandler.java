package com.app.cineticket.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tratamento central de erros da API. Todas as respostas de erro saem em
 * {@code application/problem+json} (RFC 9457) com title, status, detail, instance,
 * timestamp e correlationId.
 * <p>
 * Estende {@link ResponseEntityExceptionHandler} para cobrir as exceções do Spring MVC
 * (JSON malformado, tipo de parâmetro inválido, rota inexistente, método não suportado...),
 * que antes caíam no tratamento genérico e viravam 500.
 */
@Slf4j
@RestControllerAdvice
public class ResourceExceptionHandler extends ResponseEntityExceptionHandler {

    /** Erro de um campo na validação do corpo da requisição. */
    public record FieldErrorItem(String field, String message) {
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException e, HttpServletRequest request) {
        return build(e.getStatus(), e.getTitle(), e.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException e,
                                                                       HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Não autenticado", "E-mail ou senha inválidos.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException e,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para acessar este recurso.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                                      HttpServletRequest request) {
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Conflito",
                "A operação viola uma restrição de integridade dos dados.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Erro interno não tratado: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(new FieldErrorItem(error.getField(), messageOf(error.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(new FieldErrorItem(error.getObjectName(), messageOf(error.getDefaultMessage()))));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Um ou mais campos são inválidos.");
        problem.setTitle("Erro de validação");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    /**
     * Ponto único por onde passam as exceções do Spring MVC: traduz título e mensagem
     * para português e acrescenta instance, timestamp e correlationId.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(statusCode);
        translate(ex, problem);
        ProblemDetailFactory.enrich(problem, pathOf(request));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private void translate(Exception ex, ProblemDetail problem) {
        switch (ex) {
            case HttpMessageNotReadableException e -> describe(problem, "Corpo da requisição inválido",
                    "O JSON enviado está malformado ou contém valores inválidos.");
            case MethodArgumentTypeMismatchException e -> describe(problem, "Parâmetro inválido",
                    "O parâmetro '" + e.getName() + "' está em um formato inválido.");
            case TypeMismatchException e -> describe(problem, "Parâmetro inválido",
                    "Um dos parâmetros está em um formato inválido.");
            case MissingServletRequestParameterException e -> describe(problem, "Parâmetro obrigatório ausente",
                    "O parâmetro '" + e.getParameterName() + "' é obrigatório.");
            case NoResourceFoundException e -> describe(problem, "Recurso não encontrado",
                    "A rota solicitada não existe.");
            case HttpRequestMethodNotSupportedException e -> describe(problem, "Método não permitido",
                    "O método " + e.getMethod() + " não é suportado nesta rota.");
            case HttpMediaTypeNotSupportedException e -> describe(problem, "Tipo de conteúdo não suportado",
                    "Envie o corpo da requisição como application/json.");
            case ResponseStatusException e when e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value() ->
                    problem.setTitle("Muitas requisições");
            default -> {
                // Mantém o título e a mensagem padrão do Spring.
            }
        }
    }

    private static void describe(ProblemDetail problem, String title, String detail) {
        problem.setTitle(title);
        problem.setDetail(detail);
    }

    private static ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
                                                       HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(status, title, detail, request.getRequestURI());
        return ResponseEntity.status(status).body(problem);
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    private static String messageOf(String message) {
        return Objects.requireNonNullElse(message, "valor inválido");
    }
}
