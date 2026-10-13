package com.app.cineticket.exception;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.Set;

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
            "{\"nome\":\"Cine Teste\",\"cnpj\":\"12345678000199\",\"endereco\":\"Rua A, 1\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CinemaRepository cinemaRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        cinemaRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role userRole = roleRepository.save(role("ROLE_USER"));
        Role adminRole = roleRepository.save(role("ROLE_ADMIN"));
        adminToken = tokenService.generateToken(userRepository.save(admin(userRole, adminRole)));
    }

    @Test
    void recursoInexistenteRetorna404() throws Exception {
        mockMvc.perform(asAdmin(get(CINEMAS + "/999999")))
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
        mockMvc.perform(asAdmin(get(CINEMAS + "/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mockMvc.perform(asAdmin(post(CINEMAS)).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
    }

    @Test
    void validacaoRetorna422ComListaDeCampos() throws Exception {
        mockMvc.perform(asAdmin(post(CINEMAS)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Erro de validação"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("nome")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("cnpj")))
                .andExpect(jsonPath("$.errors[*].message").exists());
    }

    @Test
    void cnpjDuplicadoRetorna409() throws Exception {
        mockMvc.perform(asAdmin(post(CINEMAS)).contentType(MediaType.APPLICATION_JSON).content(VALID_CINEMA))
                .andExpect(status().isCreated());

        mockMvc.perform(asAdmin(post(CINEMAS)).contentType(MediaType.APPLICATION_JSON).content(VALID_CINEMA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void rotaInexistenteRetorna404() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/rota-que-nao-existe")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(asAdmin(patch(CINEMAS)).contentType(MediaType.APPLICATION_JSON).content(VALID_CINEMA))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    void respostaDeErroDevolveCorrelationIdRecebido() throws Exception {
        mockMvc.perform(asAdmin(get(CINEMAS + "/999999")).header("X-Correlation-ID", "teste-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", "teste-123"))
                .andExpect(jsonPath("$.correlationId").value("teste-123"));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + adminToken);
    }

    private static Role role(String nome) {
        Role role = new Role();
        role.setNome(nome);
        return role;
    }

    private static User admin(Role... roles) {
        User user = new User();
        user.setNome("Admin");
        user.setEmail("admin@teste.com");
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }
}
