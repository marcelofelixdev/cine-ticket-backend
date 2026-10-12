package com.app.cineticket.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS para o frontend: origem permitida e cabeçalhos expostos ao JavaScript.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "api.security.token.secret=test_secret_for_ci_with_at_least_32_chars",
        "TMDB_API_KEY=test_tmdb_key",
        "app.bootstrap.admin.enabled=false"
})
@AutoConfigureMockMvc
class CorsConfigTest {

    private static final String FRONTEND = "http://localhost:3000";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    @Test
    void expoeCabecalhosUteisAoFrontend() throws Exception {
        mockMvc.perform(get("/api/v1/movies").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Expose-Headers", allOf(
                        containsString("Content-Disposition"),
                        containsString("X-Correlation-ID"),
                        containsString("Location"))));
    }

    @Test
    void preflightAceitaCorrelationIdEnviadoPeloFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/tickets")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, X-Correlation-ID"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Correlation-ID")));
    }

    @Test
    void origemNaoPermitidaNaoRecebeCabecalhosCors() throws Exception {
        mockMvc.perform(get("/api/v1/movies").header("Origin", "https://site-desconhecido.com"))
                .andExpect(status().isForbidden());
    }
}
