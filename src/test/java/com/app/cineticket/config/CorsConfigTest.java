package com.app.cineticket.config;

import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class CorsConfigTest extends AbstractIntegrationTest {

    private static final String FRONTEND = "http://localhost:3000";

    @Autowired private MockMvc mockMvc;

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
