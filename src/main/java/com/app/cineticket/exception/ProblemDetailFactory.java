package com.app.cineticket.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * Monta o corpo de erro padrão da API (ProblemDetail, RFC 9457).
 * Usado pelo {@link ResourceExceptionHandler} e pela camada de segurança,
 * para que todos os erros tenham o mesmo formato.
 */
public final class ProblemDetailFactory {

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(HttpStatusCode status, String title, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        enrich(problem, path);
        return problem;
    }

    /** Acrescenta instance, timestamp e correlationId a um ProblemDetail já existente. */
    public static void enrich(ProblemDetail problem, String path) {
        if (problem.getInstance() == null && path != null) {
            problem.setInstance(toUri(path));
        }
        problem.setProperty("timestamp", Instant.now());
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }

    private static URI toUri(String path) {
        try {
            return URI.create(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
