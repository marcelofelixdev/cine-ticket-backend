package com.app.cineticket.exception;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static com.app.cineticket.support.ApiRequests.authed;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que os erros da API saem em ProblemDetail com o status HTTP correto,
 * inclusive os que antes caíam no tratamento genérico e viravam 500.
 */
class ApiErrorResponsesTest extends AbstractIntegrationTest {

    private static final String CINEMAS = "/api/v1/cinemas";
    private static final String VALID_CINEMA =
            "{\"nome\":\"Cine Teste\",\"cnpj\":\"12345678000199\",\"endereco\":\"Rua A, 1\","
                    + "\"cidade\":\"São Paulo\",\"uf\":\"SP\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private String adminToken;

    @BeforeEach
    void setUp() {
        testData.deleteCatalog();
        testData.deleteUsers("admin@teste.com");
        adminToken = testData.token("admin@teste.com", "ROLE_ADMIN", null);
    }

    @Test
    void recursoInexistenteRetorna404() throws Exception {
        mockMvc.perform(authed(get(CINEMAS + "/999999"), adminToken, null))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.instance").value(CINEMAS + "/999999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void idEmFormatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(authed(get(CINEMAS + "/abc"), adminToken, null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mockMvc.perform(authed(post(CINEMAS), adminToken, "{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void validacaoRetorna422ComListaDeCampos() throws Exception {
        mockMvc.perform(authed(post(CINEMAS), adminToken, "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Erro de validação"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("nome")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("cnpj")))
                .andExpect(jsonPath("$.errors[*].message").exists());
    }

    @Test
    void cnpjDuplicadoRetorna409() throws Exception {
        mockMvc.perform(authed(post(CINEMAS), adminToken, VALID_CINEMA))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post(CINEMAS), adminToken, VALID_CINEMA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void ordenacaoPorCampoInexistenteRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/movies").param("sort", "naoExiste"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parâmetro de ordenação inválido"))
                .andExpect(jsonPath("$.detail").value("Não é possível ordenar por 'naoExiste'."));
    }

    @Test
    void rotaInexistenteRetorna404() throws Exception {
        mockMvc.perform(authed(get("/api/v1/rota-que-nao-existe"), adminToken, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(authed(patch(CINEMAS), adminToken, VALID_CINEMA))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    void respostaDeErroDevolveCorrelationIdRecebido() throws Exception {
        mockMvc.perform(authed(get(CINEMAS + "/999999"), adminToken, null).header("X-Correlation-ID", "teste-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", "teste-123"))
                .andExpect(jsonPath("$.correlationId").value("teste-123"));
    }
}
