package com.app.cineticket.security;

import com.app.cineticket.exception.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve erros da camada de segurança no formato ProblemDetail (RFC 9457).
 * <p>
 * Os erros de autenticação e autorização acontecem nos filtros, antes de chegar
 * aos controllers, por isso o {@code @RestControllerAdvice} não consegue tratá-los.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetailFactory.create(status, title, detail, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
