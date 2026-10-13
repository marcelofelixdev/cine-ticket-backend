package com.app.cineticket.security;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

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
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role userRole = roleRepository.save(role("ROLE_USER"));
        Role adminRole = roleRepository.save(role("ROLE_ADMIN"));
        userToken = tokenService.generateToken(userRepository.save(user("cliente@teste.com", userRole)));
        adminToken = tokenService.generateToken(userRepository.save(user("admin@teste.com", userRole, adminRole)));
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
        mockMvc.perform(get(PROTECTED_ROUTE).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumEmRotaDeAdminRetorna403EmProblemDetail() throws Exception {
        mockMvc.perform(post(ADMIN_ROUTE)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.instance").value(ADMIN_ROUTE));
    }

    @Test
    void adminPassaPelaSegurancaEChegaNaValidacao() throws Exception {
        mockMvc.perform(post(ADMIN_ROUTE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static Role role(String nome) {
        Role role = new Role();
        role.setNome(nome);
        return role;
    }

    private static User user(String email, Role... roles) {
        User user = new User();
        user.setNome("Teste");
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }
}
