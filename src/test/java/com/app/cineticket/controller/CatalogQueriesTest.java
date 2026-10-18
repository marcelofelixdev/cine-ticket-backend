package com.app.cineticket.controller;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultas do item 9: filtros de sessões e os endpoints de detalhe
 * (sessão, sala, ingresso e usuário logado).
 */
class CatalogQueriesTest extends AbstractIntegrationTest {

    private static final String SESSIONS = "/api/v1/sessions";
    private static final String ADMIN_EMAIL = "admin@catalogo.teste";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private SessionRepository sessionRepository;

    private User owner;
    private String ownerToken;
    private String otherToken;
    private Movie movieA;
    private Cinema cinema1;
    private Cinema cinema2;
    private Room room1;
    private Session sessionAmanhaFilmeA;
    private Session sessionInativa;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testData.customer("dono@teste.com");
        ownerToken = testData.token(owner);
        otherToken = testData.token(testData.customer("outro@teste.com"));

        cinema1 = testData.cinema("Cine Centro", "11111111000111");
        cinema2 = testData.cinema("Cine Shopping", "22222222000122");
        room1 = testData.room("Sala 1", cinema1, 10);
        Room room2 = testData.room("Sala 2", cinema2, 10);
        movieA = testData.movie("Filme A", 120);
        Movie movieB = testData.movie("Filme B", 120);

        LocalDate amanha = LocalDate.now().plusDays(1);
        testData.session(movieA, room1, LocalDateTime.now().minusDays(1));
        sessionAmanhaFilmeA = testData.session(movieA, room1, amanha.atTime(15, 0));
        testData.session(movieB, room2, amanha.plusDays(1).atTime(15, 0));
        sessionInativa = testData.session(movieB, room2, amanha.atTime(20, 0));
        sessionInativa.setAtivo(false);
        sessionInativa = sessionRepository.save(sessionInativa);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers("dono@teste.com", "outro@teste.com", ADMIN_EMAIL);
    }

    @Test
    void listaDeSalasFiltraPorCinema() throws Exception {
        mockMvc.perform(authed(get("/api/v1/rooms").param("cinemaId", String.valueOf(cinema1.getId())), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Sala 1"));
        mockMvc.perform(authed(get("/api/v1/rooms"), ownerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void filmeComSessoesNaoPodeSerExcluido() throws Exception {
        String adminToken = testData.token(ADMIN_EMAIL, "ROLE_ADMIN", null);
        Movie semSessao = testData.movie("Filme Sem Sessão", 90);

        mockMvc.perform(authed(delete("/api/v1/movies/" + movieA.getId()), adminToken, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O filme tem sessões cadastradas e não pode ser excluído."));
        mockMvc.perform(authed(delete("/api/v1/movies/" + semSessao.getId()), adminToken, null))
                .andExpect(status().isNoContent());
    }

    @Test
    void listaPorPadraoSoSessoesAtivasEFuturas() throws Exception {
        mockMvc.perform(authed(get(SESSIONS), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void incluiSessoesPassadasQuandoPedido() throws Exception {
        mockMvc.perform(authed(get(SESSIONS).param("includePast", "true"), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void filtraPorFilme() throws Exception {
        mockMvc.perform(authed(get(SESSIONS).param("movieId", movieA.getId().toString()), ownerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movieTitulo").value("Filme A"));
    }

    @Test
    void filtraPorCinema() throws Exception {
        mockMvc.perform(authed(get(SESSIONS).param("cinemaId", cinema2.getId().toString()), ownerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cinemaNome").value("Cine Shopping"));
    }

    @Test
    void filtraPorData() throws Exception {
        mockMvc.perform(authed(get(SESSIONS).param("date", LocalDate.now().plusDays(1).toString()), ownerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sessionAmanhaFilmeA.getId()));
    }

    @Test
    void detalheDaSessaoAtivaENaoMostraSessaoCancelada() throws Exception {
        mockMvc.perform(authed(get(SESSIONS + "/" + sessionAmanhaFilmeA.getId()), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(movieA.getId()))
                .andExpect(jsonPath("$.movieTitulo").value("Filme A"))
                .andExpect(jsonPath("$.roomId").value(room1.getId()))
                .andExpect(jsonPath("$.cinemaId").value(cinema1.getId()))
                .andExpect(jsonPath("$.cinemaNome").value("Cine Centro"));

        mockMvc.perform(authed(get(SESSIONS + "/" + sessionInativa.getId()), ownerToken, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalheDaSala() throws Exception {
        mockMvc.perform(authed(get("/api/v1/rooms/" + room1.getId()), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sala 1"))
                .andExpect(jsonPath("$.cinemaId").value(cinema1.getId()))
                .andExpect(jsonPath("$.cinemaNome").value("Cine Centro"));
    }

    @Test
    void ingressoSoPodeSerVistoPeloDono() throws Exception {
        Long ticketId = ticket().getId();

        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId), otherToken, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginacaoUsaFormatoEstavel() throws Exception {
        mockMvc.perform(authed(get(SESSIONS).param("size", "1"), ownerToken, null))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void compraResponde201ComLocationEIdsDaSessaoEDoAssento() throws Exception {
        Seat seat = seat();
        String body = "{\"sessionId\":" + sessionAmanhaFilmeA.getId() + ",\"seatId\":" + seat.getId()
                + ",\"ticketType\":\"INTEIRA\"}";

        mockMvc.perform(authed(post("/api/v1/tickets"), ownerToken, null)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.matchesPattern(".*/api/v1/tickets/\\d+$")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sessionId").value(sessionAmanhaFilmeA.getId()))
                .andExpect(jsonPath("$.seatId").value(seat.getId()))
                .andExpect(jsonPath("$.horarioSessao").exists());
    }

    @Test
    void cancelamentoDuplicadoViaDeleteFoiRemovido() throws Exception {
        Long ticketId = ticket().getId();

        mockMvc.perform(authed(delete("/api/v1/tickets/" + ticketId), ownerToken, null))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void retornaOUsuarioLogado() throws Exception {
        mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dono@teste.com"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }


    private Seat seat() {
        return testData.seat(room1, "A", 1);
    }

    private Ticket ticket() {
        return testData.ticket(sessionAmanhaFilmeA, seat(), owner, TicketStatus.PENDING);
    }
}
