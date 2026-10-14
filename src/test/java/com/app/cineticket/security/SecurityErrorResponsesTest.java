package com.app.cineticket.security;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static com.app.cineticket.support.ApiRequests.authed;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a cadeia de segurança real (filtros ligados): JWT, RBAC e o formato dos erros 401/403.
 */
class SecurityErrorResponsesTest extends AbstractIntegrationTest {

    private static final String PROTECTED_ROUTE = "/api/v1/tickets/my-tickets";
    private static final String ADMIN_ROUTE = "/api/v1/cinemas";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        testData.deleteUsers("cliente@teste.com", "admin@teste.com");
        userToken = testData.token("cliente@teste.com", "ROLE_USER", null);
        adminToken = testData.token("admin@teste.com", "ROLE_ADMIN", null);
    }

    @Test
    void semTokenRetorna401EmProblemDetail() throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.instance").value(PROTECTED_ROUTE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void usuarioAutenticadoAcessaRotaProtegida() throws Exception {
        mockMvc.perform(authed(get(PROTECTED_ROUTE), userToken, null))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumEmRotaDeAdminRetorna403EmProblemDetail() throws Exception {
        mockMvc.perform(authed(post(ADMIN_ROUTE), userToken, "{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.instance").value(ADMIN_ROUTE));
    }

    @Test
    void adminPassaPelaSegurancaEChegaNaValidacao() throws Exception {
        mockMvc.perform(authed(post(ADMIN_ROUTE), adminToken, "{}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
