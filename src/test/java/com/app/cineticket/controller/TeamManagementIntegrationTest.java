package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Equipe por cinema: admin cadastra gerentes e funcionários; gerente só funcionários do próprio cinema. */
class TeamManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@equipe.teste";
    private static final String ADMIN_TEAM = "/api/v1/admin/team";
    private static final String MANAGER_STAFF = "/api/v1/manager/staff";
    private static final String PASSWORD = "senha-forte-123";
    private static final String CNPJ_A = "41414141000141";
    private static final String CNPJ_B = "42424242000142";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CinemaRepository cinemaRepository;

    private Cinema cinemaA;
    private Cinema cinemaB;
    private String adminToken;
    private String managerToken;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaA = cinema("Cine A", CNPJ_A);
        cinemaB = cinema("Cine B", CNPJ_B);
        adminToken = token(user("admin", "ROLE_ADMIN", null));
        managerToken = token(user("gerente-a", "ROLE_MANAGER", cinemaA));
        staffToken = token(user("funcionario-b", "ROLE_STAFF", cinemaB));
        clientToken = token(user("cliente", "ROLE_USER", null));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(DOMAIN))
                .toList());
        cinemaRepository.findAll().stream()
                .filter(cinema -> Set.of(CNPJ_A, CNPJ_B).contains(cinema.getCnpj()))
                .forEach(cinemaRepository::delete);
    }

    @Test
    void adminCadastraGerenteNoCinema() throws Exception {
        String body = memberJson("novo-gerente", "MANAGER", cinemaB.getId());

        perform(post(ADMIN_TEAM), adminToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("MANAGER"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaB.getId()))
                .andExpect(jsonPath("$.cinemaNome").value("Cine B"));

        User created = userRepository.findByEmailIgnoreCase("novo-gerente" + DOMAIN).orElseThrow();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value(cinemaB.getId()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_MANAGER"));
    }

    @Test
    void adminComCinemaInexistenteRecebe404() throws Exception {
        perform(post(ADMIN_TEAM), adminToken, memberJson("sem-cinema", "STAFF", 999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailRepetidoRecebe409() throws Exception {
        perform(post(ADMIN_TEAM), adminToken, memberJson("gerente-a", "STAFF", cinemaA.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void gerenteCadastraFuncionarioSempreNoProprioCinema() throws Exception {
        // Papel e cinema no corpo são ignorados: o funcionário vai para o cinema do gerente.
        perform(post(MANAGER_STAFF), managerToken, memberJson("caixa-a", "MANAGER", cinemaB.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("STAFF"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaA.getId()));
    }

    @Test
    void gerenteListaSoOsFuncionariosDoProprioCinema() throws Exception {
        user("caixa-a", "ROLE_STAFF", cinemaA);

        perform(get(MANAGER_STAFF), managerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("caixa-a" + DOMAIN));
    }

    @Test
    void adminListaAEquipePorCinema() throws Exception {
        perform(get(ADMIN_TEAM + "?cinemaId=" + cinemaA.getId()), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].papel").value("MANAGER"));
    }

    @Test
    void gerenteSemCinemaRecebe403() throws Exception {
        String orphanToken = token(user("gerente-sem-cinema", "ROLE_MANAGER", null));

        perform(get(MANAGER_STAFF), orphanToken, null).andExpect(status().isForbidden());
    }

    @Test
    void cadaPapelSoAcessaAsPropriasRotas() throws Exception {
        String body = memberJson("tentativa", "MANAGER", cinemaA.getId());
        for (String token : new String[]{managerToken, staffToken, clientToken}) {
            perform(post(ADMIN_TEAM), token, body).andExpect(status().isForbidden());
            perform(get(ADMIN_TEAM), token, null).andExpect(status().isForbidden());
        }
        for (String token : new String[]{adminToken, staffToken, clientToken}) {
            perform(post(MANAGER_STAFF), token, body).andExpect(status().isForbidden());
            perform(get(MANAGER_STAFF), token, null).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(ADMIN_TEAM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MANAGER_STAFF)).andExpect(status().isUnauthorized());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private static String memberJson(String login, String papel, Long cinemaId) {
        return """
                {"nome":"Pessoa %s","email":"%s%s","senha":"%s","papel":"%s","cinemaId":%d}
                """.formatted(login, login, DOMAIN, PASSWORD, papel, cinemaId);
    }

    private Cinema cinema(String nome, String cnpj) {
        Cinema cinema = new Cinema();
        cinema.setNome(nome);
        cinema.setCnpj(cnpj);
        cinema.setEndereco("Rua da Equipe, 1");
        return cinemaRepository.save(cinema);
    }

    private User user(String login, String role, Cinema cinema) {
        User user = new User();
        user.setNome("Pessoa " + login);
        user.setEmail(login + DOMAIN);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByNome(role).orElseThrow())));
        user.setCinema(cinema);
        return userRepository.save(user);
    }

    private String token(User user) {
        return tokenService.generateToken(user);
    }
}
