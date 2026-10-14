package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.domain.enums.SessionAudio;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sessões criadas pelo gerente no próprio cinema, cidade dos cinemas e "Em breve". */
class ManagerSessionIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@sessoes.teste";
    private static final String CNPJ_A = "46464646000146";
    private static final String CNPJ_B = "47474747000147";
    private static final String MANAGER_SESSIONS = "/api/v1/manager/sessions";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;

    private Cinema cinemaA;
    private Room roomA;
    private Room roomB;
    private Movie emCartaz;
    private Movie foraDaProgramacao;
    private String adminToken;
    private String managerToken;
    private String staffToken;
    private String clientToken;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaA = testData.cinema("Cine Sessão A", CNPJ_A, "São Paulo", "SP");
        Cinema cinemaB = testData.cinema("Cine Sessão B", CNPJ_B, "Rio de Janeiro", "RJ");
        roomA = testData.room("Sala A1", cinemaA, 50);
        roomB = testData.room("Sala B1", cinemaB, 50);
        emCartaz = testData.movie("Filme em Cartaz", 120);
        foraDaProgramacao = testData.movie("Filme Fora", 120);
        testData.program(cinemaA, emCartaz, ProgramacaoStatus.ATIVO);
        testData.program(cinemaB, emCartaz, ProgramacaoStatus.ATIVO);
        day = LocalDate.now().plusDays(2);

        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        managerToken = testData.token("gerente" + DOMAIN, "ROLE_MANAGER", cinemaA);
        staffToken = testData.token("funcionario" + DOMAIN, "ROLE_STAFF", cinemaA);
        clientToken = testData.token("cliente" + DOMAIN, "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
    }

    @Test
    void gerenteCriaSessaoNoProprioCinemaComFormatoEAudio() throws Exception {
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, sessionJson(emCartaz, roomA, "14:00", "3D", "DUBLADO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formato").value("3D"))
                .andExpect(jsonPath("$.audio").value("DUBLADO"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaA.getId()));
    }

    @Test
    void semFormatoEAudioUsaOPadrao2DLegendado() throws Exception {
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, sessionJson(emCartaz, roomA, "14:00", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formato").value("2D"))
                .andExpect(jsonPath("$.audio").value("LEGENDADO"));
    }

    @Test
    void salaDeOutroCinemaRecebe403() throws Exception {
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, sessionJson(emCartaz, roomB, "14:00", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void filmeForaDaProgramacaoRecebe422InclusiveParaOAdmin() throws Exception {
        String body = sessionJson(foraDaProgramacao, roomA, "14:00", null, null);
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, body)).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(authed(post("/api/v1/sessions"), adminToken, body)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void conflitoDeHorarioNaSalaContinuaDando409() throws Exception {
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, sessionJson(emCartaz, roomA, "14:00", null, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(post(MANAGER_SESSIONS), managerToken, sessionJson(emCartaz, roomA, "15:00", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void listaDoDiaTrazSoAsSessoesDoProprioCinemaComOcupacao() throws Exception {
        testData.session(emCartaz, roomA, day.atTime(14, 0));
        testData.session(emCartaz, roomB, day.atTime(14, 0));

        mockMvc.perform(authed(get(MANAGER_SESSIONS).param("date", day.toString()), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomNome").value("Sala A1"))
                .andExpect(jsonPath("$[0].lugares").value(50))
                .andExpect(jsonPath("$[0].ocupados").value(0));

        mockMvc.perform(authed(get("/api/v1/manager/rooms"), managerToken, null))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Sala A1"));
    }

    @Test
    void gerenteCancelaSoSessaoDoProprioCinema() throws Exception {
        Session own = testData.session(emCartaz, roomA, day.atTime(14, 0));
        Session other = testData.session(emCartaz, roomB, day.atTime(14, 0));

        mockMvc.perform(authed(delete(MANAGER_SESSIONS + "/" + other.getId()), managerToken, null))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(delete(MANAGER_SESSIONS + "/" + own.getId()), managerToken, null))
                .andExpect(status().isNoContent());

        assertFalse(sessionRepository.findById(own.getId()).orElseThrow().getAtivo());
    }

    @Test
    void cidadesFiltrosEEmBreve() throws Exception {
        Movie futuro = testData.movie("Filme do Mês Que Vem", 120);
        futuro.setDataEstreia(LocalDate.now().plusDays(30));
        movieRepository.save(futuro);
        testData.program(cinemaA, futuro, ProgramacaoStatus.ATIVO);
        Session dublada = testData.session(emCartaz, roomA, day.atTime(14, 0));
        dublada.setAudio(SessionAudio.DUBLADO);
        sessionRepository.save(dublada);
        testData.session(emCartaz, roomA, day.atTime(18, 0));

        mockMvc.perform(get("/api/v1/cinemas/cidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cidade", hasItem("São Paulo")))
                .andExpect(jsonPath("$[*].cidade", hasItem("Rio de Janeiro")));
        mockMvc.perform(get("/api/v1/cinemas").param("cidade", "são paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].uf").value("SP"));

        mockMvc.perform(get("/api/v1/movies").param("cidade", "São Paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Filme em Cartaz"));
        mockMvc.perform(get("/api/v1/movies/em-breve").param("cidade", "São Paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Filme do Mês Que Vem"));
        mockMvc.perform(get("/api/v1/movies/em-breve").param("cidade", "Rio de Janeiro"))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(get("/api/v1/sessions")
                        .param("cinemaId", String.valueOf(cinemaA.getId()))
                        .param("audio", "DUBLADO"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].audio").value("DUBLADO"));
    }

    @Test
    void cinemaExigeCidadeEUf() throws Exception {
        mockMvc.perform(authed(post("/api/v1/cinemas"), adminToken,
                        "{\"nome\":\"Cine Sem Cidade\",\"cnpj\":\"48484848000148\",\"endereco\":\"Rua X, 1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItem("cidade")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("uf")));
    }

    @Test
    void soOGerenteAcessaAsRotasDeSessaoDoCinema() throws Exception {
        String body = sessionJson(emCartaz, roomA, "14:00", null, null);
        for (String token : List.of(adminToken, staffToken, clientToken)) {
            mockMvc.perform(authed(get(MANAGER_SESSIONS), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(get("/api/v1/manager/rooms"), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(post(MANAGER_SESSIONS), token, body)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(MANAGER_SESSIONS)).andExpect(status().isUnauthorized());
    }

    private String sessionJson(Movie movie, Room room, String time, String formato, String audio) {
        return "{\"horarioInicio\":\"" + day + "T" + time + ":00\""
                + ",\"valorBase\":30.00"
                + ",\"movieId\":" + movie.getId()
                + ",\"roomId\":" + room.getId()
                + (formato == null ? "" : ",\"formato\":\"" + formato + "\"")
                + (audio == null ? "" : ",\"audio\":\"" + audio + "\"")
                + "}";
    }
}
