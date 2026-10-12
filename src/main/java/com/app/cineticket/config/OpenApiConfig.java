package com.app.cineticket.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI (contrato oficial da API).
 * <p>
 * Declara o esquema de autenticação Bearer (JWT), o que habilita o botão
 * "Authorize" no Swagger UI. Rotas públicas continuam funcionando sem token.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI cineTicketOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineTicket API")
                        .version("v1")
                        .description("""
                                API de bilheteria de cinema: catálogo de filmes, cinemas e sessões, \
                                mapa de assentos, compra de ingressos com pagamento assíncrono e \
                                ingresso em PDF com QR Code.

                                Projeto de estudo e portfólio: o pagamento é simulado.

                                Para rotas protegidas, faça login em POST /api/v1/auth/login e use o \
                                token no botão Authorize. Erros seguem o formato ProblemDetail (RFC 9457).""")
                        .license(new License().name("Uso educacional")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
