package com.app.cineticket.config;

import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A especificação OpenAPI e o Swagger UI ficam públicos e descrevem as rotas da API.
 */
class OpenApiDocsTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void especificacaoPublicaDescreveAsRotasEAAutenticacao() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CineTicket API"))
                .andExpect(jsonPath("$.paths['/api/v1/sessions/{id}/seats']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tickets']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void swaggerUiPublico() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
