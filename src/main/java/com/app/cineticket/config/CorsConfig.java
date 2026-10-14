package com.app.cineticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", CorrelationIdFilter.HEADER)
                // Sem isso o navegador esconde esses cabeçalhos do JavaScript do frontend:
                // nome do arquivo do PDF, ID de rastreio dos erros e URL do recurso criado (201).
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION, CorrelationIdFilter.HEADER, HttpHeaders.LOCATION)
                // Necessário para o navegador enviar o cookie HttpOnly do refresh token.
                // Seguro porque as origens são uma lista explícita (nunca "*").
                .allowCredentials(true)
                .maxAge(3600);
    }
}
