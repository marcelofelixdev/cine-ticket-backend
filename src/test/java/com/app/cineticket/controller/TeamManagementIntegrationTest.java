package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.app.cineticket.support.ApiRequests.authed;
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
    @Autowired private TestData testData;
    @Autowired private UserRepository userRepository;

    private Cinema cinemaA;
    private Cinema cinemaB;
    private String adminToken;
    private String managerToken;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaA = testData.cinema("Cine A", CNPJ_A);
        cinemaB = testData.cinema("Cine B", CNPJ_B);
        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        managerToken = testData.token("gerente-a" + DOMAIN, "ROLE_MANAGER", cinemaA);
        staffToken = testData.token("funcionario-b" + DOMAIN, "ROLE_STAFF", cinemaB);
        clientToken = testData.token("cliente" + DOMAIN, "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
    }

    @Test
    void adminCadastraGerenteNoCinema() throws Exception {
        mockMvc.perform(authed(post(ADMIN_TEAM), adminToken, memberJson("novo-gerente", "MANAGER", cinemaB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("MANAGER"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaB.getId()))
                .andExpect(jsonPath("$.cinemaNome").value("Cine B"));

        User created = userRepository.findByEmailIgnoreCase("novo-gerente" + DOMAIN).orElseThrow();
        mockMvc.perform(authed(get("/api/v1/users/me"), testData.token(created), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value(cinemaB.getId()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_MANAGER"));
    }

    @Test
    void adminComCinemaInexistenteRecebe404() throws Exception {
        mockMvc.perform(authed(post(ADMIN_TEAM), adminToken, memberJson("sem-cinema", "STAFF", 999_999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailRepetidoRecebe409() throws Exception {
        mockMvc.perform(authed(post(ADMIN_TEAM), adminToken, memberJson("gerente-a", "STAFF", cinemaA.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void gerenteCadastraFuncionarioSempreNoProprioCinema() throws Exception {
        // Papel e cinema no corpo são ignorados: o funcionário vai para o cinema do gerente.
        mockMvc.perform(authed(post(MANAGER_STAFF), managerToken, memberJson("caixa-a", "MANAGER", cinemaB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("STAFF"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaA.getId()));
    }

    @Test
    void gerenteListaSoOsFuncionariosDoProprioCinema() throws Exception {
        testData.user("caixa-a" + DOMAIN, "ROLE_STAFF", cinemaA);

        mockMvc.perform(authed(get(MANAGER_STAFF), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("caixa-a" + DOMAIN));
    }

    @Test
    void adminListaAEquipePorCinema() throws Exception {
        mockMvc.perform(authed(get(ADMIN_TEAM + "?cinemaId=" + cinemaA.getId()), adminToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].papel").value("MANAGER"));
    }

    @Test
    void gerenteSemCinemaRecebe403() throws Exception {
        String orphanToken = testData.token("gerente-sem-cinema" + DOMAIN, "ROLE_MANAGER", null);

        mockMvc.perform(authed(get(MANAGER_STAFF), orphanToken, null)).andExpect(status().isForbidden());
    }

    @Test
    void cadaPapelSoAcessaAsPropriasRotas() throws Exception {
        String body = memberJson("tentativa", "MANAGER", cinemaA.getId());
        for (String token : List.of(managerToken, staffToken, clientToken)) {
            mockMvc.perform(authed(post(ADMIN_TEAM), token, body)).andExpect(status().isForbidden());
            mockMvc.perform(authed(get(ADMIN_TEAM), token, null)).andExpect(status().isForbidden());
        }
        for (String token : List.of(adminToken, staffToken, clientToken)) {
            mockMvc.perform(authed(post(MANAGER_STAFF), token, body)).andExpect(status().isForbidden());
            mockMvc.perform(authed(get(MANAGER_STAFF), token, null)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(ADMIN_TEAM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MANAGER_STAFF)).andExpect(status().isUnauthorized());
    }

    private static String memberJson(String login, String papel, Long cinemaId) {
        return """
                {"nome":"Pessoa %s","email":"%s%s","senha":"%s","papel":"%s","cinemaId":%d}
                """.formatted(login, login, DOMAIN, PASSWORD, papel, cinemaId);
    }
}
