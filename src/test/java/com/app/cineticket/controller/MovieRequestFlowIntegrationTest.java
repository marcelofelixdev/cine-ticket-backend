package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieRequestRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedidos de filme e programação por cinema. Os filmes já estão no catálogo (com tmdbId),
 * então nenhuma chamada chega ao TMDB.
 */
class MovieRequestFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@pedidos.teste";
    private static final String CNPJ_A = "51515151000151";
    private static final String CNPJ_B = "45454545000145";
    private static final long TMDB_ID = 693134L;
    private static final String REQUEST_BODY = "{\"tmdbId\":" + TMDB_ID + "}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestData testData;
    @Autowired private MovieRepository movieRepository;
    @Autowired private CinemaMovieRepository cinemaMovieRepository;
    @Autowired private MovieRequestRepository movieRequestRepository;

    private Cinema cinemaA;
    private Cinema cinemaB;
    private Movie movie;
    private String adminToken;
    private String managerAToken;
    private String managerBToken;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaA = testData.cinema("Cine Pedido A", CNPJ_A);
        cinemaB = testData.cinema("Cine Pedido B", CNPJ_B);
        movie = testData.movie("Duna: Parte Dois", 167);
        movie.setTmdbId(String.valueOf(TMDB_ID));
        movie = movieRepository.save(movie);

        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        managerAToken = testData.token("gerente-a" + DOMAIN, "ROLE_MANAGER", cinemaA);
        managerBToken = testData.token("gerente-b" + DOMAIN, "ROLE_MANAGER", cinemaB);
        staffToken = testData.token("funcionario-a" + DOMAIN, "ROLE_STAFF", cinemaA);
        clientToken = testData.token("cliente" + DOMAIN, "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        movieRequestRepository.deleteAll();
        cinemaMovieRepository.deleteAll();
        movieRepository.deleteAll();
        testData.deleteUsersEndingWith(DOMAIN);
        testData.deleteCinemas(Set.of(CNPJ_A, CNPJ_B));
    }

    @Test
    void filmeSemProgramacaoNaoApareceNoCatalogoPublico() throws Exception {
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void gerentePedeAdminAprovaEOFilmeEntraEmCartaz() throws Exception {
        long requestId = createRequest(managerAToken);

        mockMvc.perform(authed(get("/api/v1/admin/movie-requests").param("status", "AGUARDANDO_ADMIN"), adminToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cinemaNome").value("Cine Pedido A"))
                .andExpect(jsonPath("$[0].solicitadoPor").value("gerente-a" + DOMAIN));

        mockMvc.perform(authed(post(approve(requestId)), adminToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.filmeId").value(movie.getId()));

        assertEquals(ProgramacaoStatus.ATIVO,
                cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaA.getId(), movie.getId()).orElseThrow().getStatus());
        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaA.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaB.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // Decidir de novo o mesmo pedido: 409.
        mockMvc.perform(authed(post(approve(requestId)), adminToken, null)).andExpect(status().isConflict());
    }

    @Test
    void recusaExigeMotivoEOGerenteVeOMotivo() throws Exception {
        long requestId = createRequest(managerAToken);
        String reject = "/api/v1/admin/movie-requests/" + requestId + "/reject";

        mockMvc.perform(authed(post(reject), adminToken, "{\"motivo\":\" \"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(authed(post(reject), adminToken, "{\"motivo\":\"Sem sala livre neste mês\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        mockMvc.perform(authed(get("/api/v1/manager/movie-requests"), managerAToken, null))
                .andExpect(jsonPath("$[0].status").value("RECUSADO"))
                .andExpect(jsonPath("$[0].motivoRecusa").value("Sem sala livre neste mês"));
        // O gerente de outro cinema não vê o pedido.
        mockMvc.perform(authed(get("/api/v1/manager/movie-requests"), managerBToken, null))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pedidoRepetidoOuDeFilmeJaEmCartazRecebe409() throws Exception {
        long requestId = createRequest(managerAToken);
        mockMvc.perform(authed(post("/api/v1/manager/movie-requests"), managerAToken, REQUEST_BODY))
                .andExpect(status().isConflict());

        mockMvc.perform(authed(post(approve(requestId)), adminToken, null)).andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/v1/manager/movie-requests"), managerAToken, REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este filme já está em cartaz no seu cinema."));

        assertEquals(1, movieRequestRepository.findAll().stream()
                .filter(request -> request.getStatus() == MovieRequestStatus.APROVADO).count());
    }

    @Test
    void adminEnviaParaDoisCinemasESoOQueConfirmaFicaAtivo() throws Exception {
        mockMvc.perform(authed(post(offer()), adminToken, offerBody(cinemaA, cinemaB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO_GERENTE"));

        mockMvc.perform(authed(get("/api/v1/manager/programacao"), managerBToken, null))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO_GERENTE"));
        mockMvc.perform(authed(post(confirm()), managerBToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaB.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaA.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(authed(get("/api/v1/admin/movies"), adminToken, null))
                .andExpect(jsonPath("$.content[0].cinemas", hasSize(2)));

        // Enviado e ainda não confirmado: pedir o mesmo filme não faz sentido.
        mockMvc.perform(authed(post("/api/v1/manager/movie-requests"), managerAToken, REQUEST_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void gerenteNaoConfirmaFilmeQueNaoFoiEnviadoAoSeuCinema() throws Exception {
        mockMvc.perform(authed(post(offer()), adminToken, offerBody(cinemaB))).andExpect(status().isOk());

        mockMvc.perform(authed(post(confirm()), managerAToken, null)).andExpect(status().isNotFound());
        assertEquals(ProgramacaoStatus.AGUARDANDO_GERENTE,
                cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaB.getId(), movie.getId()).orElseThrow().getStatus());
    }

    @Test
    void cadaPapelSoAcessaAsPropriasRotas() throws Exception {
        for (String token : List.of(managerAToken, staffToken, clientToken)) {
            mockMvc.perform(authed(get("/api/v1/admin/movie-requests"), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(get("/api/v1/admin/movies"), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(post(offer()), token, offerBody(cinemaA))).andExpect(status().isForbidden());
        }
        for (String token : List.of(adminToken, staffToken, clientToken)) {
            mockMvc.perform(authed(get("/api/v1/manager/programacao"), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(post("/api/v1/manager/movie-requests"), token, REQUEST_BODY))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/manager/programacao")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/movie-requests")).andExpect(status().isUnauthorized());
    }

    private long createRequest(String managerToken) throws Exception {
        String body = mockMvc.perform(authed(post("/api/v1/manager/movie-requests"), managerToken, REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_ADMIN"))
                .andExpect(jsonPath("$.titulo").value("Duna: Parte Dois"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static String approve(long requestId) {
        return "/api/v1/admin/movie-requests/" + requestId + "/approve";
    }

    private String offer() {
        return "/api/v1/admin/movies/" + movie.getId() + "/offer";
    }

    private String confirm() {
        return "/api/v1/manager/programacao/" + movie.getId() + "/confirm";
    }

    private static String offerBody(Cinema... cinemas) {
        return "{\"cinemaIds\":[" + String.join(",",
                java.util.Arrays.stream(cinemas).map(cinema -> String.valueOf(cinema.getId())).toList()) + "]}";
    }
}
