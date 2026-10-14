package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieRequestRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestRoles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final String CNPJ_A = "44444444000144";
    private static final String CNPJ_B = "45454545000145";
    private static final long TMDB_ID = 693134L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CinemaRepository cinemaRepository;
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
        cinemaA = cinema("Cine Pedido A", CNPJ_A);
        cinemaB = cinema("Cine Pedido B", CNPJ_B);
        movie = new Movie();
        movie.setTitulo("Duna: Parte Dois");
        movie.setDuracaoEmMinutos(167);
        movie.setTmdbId(String.valueOf(TMDB_ID));
        movie = movieRepository.save(movie);

        adminToken = token("admin", "ROLE_ADMIN", null);
        managerAToken = token("gerente-a", "ROLE_MANAGER", cinemaA);
        managerBToken = token("gerente-b", "ROLE_MANAGER", cinemaB);
        staffToken = token("funcionario-a", "ROLE_STAFF", cinemaA);
        clientToken = token("cliente", "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        movieRequestRepository.deleteAll();
        cinemaMovieRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(DOMAIN))
                .toList());
        cinemaRepository.findAll().stream()
                .filter(cinema -> Set.of(CNPJ_A, CNPJ_B).contains(cinema.getCnpj()))
                .forEach(cinemaRepository::delete);
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

        perform(get("/api/v1/admin/movie-requests").param("status", "AGUARDANDO_ADMIN"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cinemaNome").value("Cine Pedido A"))
                .andExpect(jsonPath("$[0].solicitadoPor").value("gerente-a" + DOMAIN));

        perform(post("/api/v1/admin/movie-requests/" + requestId + "/approve"), adminToken, null)
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
        perform(post("/api/v1/admin/movie-requests/" + requestId + "/approve"), adminToken, null)
                .andExpect(status().isConflict());
    }

    @Test
    void recusaExigeMotivoEOGerenteVeOMotivo() throws Exception {
        long requestId = createRequest(managerAToken);

        perform(post("/api/v1/admin/movie-requests/" + requestId + "/reject"), adminToken, "{\"motivo\":\" \"}")
                .andExpect(status().isUnprocessableEntity());
        perform(post("/api/v1/admin/movie-requests/" + requestId + "/reject"), adminToken,
                "{\"motivo\":\"Sem sala livre neste mês\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        perform(get("/api/v1/manager/movie-requests"), managerAToken, null)
                .andExpect(jsonPath("$[0].status").value("RECUSADO"))
                .andExpect(jsonPath("$[0].motivoRecusa").value("Sem sala livre neste mês"));
        // O gerente de outro cinema não vê o pedido.
        perform(get("/api/v1/manager/movie-requests"), managerBToken, null)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pedidoRepetidoOuDeFilmeJaEmCartazRecebe409() throws Exception {
        long requestId = createRequest(managerAToken);
        perform(post("/api/v1/manager/movie-requests"), managerAToken, "{\"tmdbId\":" + TMDB_ID + "}")
                .andExpect(status().isConflict());

        perform(post("/api/v1/admin/movie-requests/" + requestId + "/approve"), adminToken, null)
                .andExpect(status().isOk());
        perform(post("/api/v1/manager/movie-requests"), managerAToken, "{\"tmdbId\":" + TMDB_ID + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este filme já está em cartaz no seu cinema."));

        assertEquals(1, movieRequestRepository.findAll().stream()
                .filter(request -> request.getStatus() == MovieRequestStatus.APROVADO).count());
    }

    @Test
    void adminEnviaParaDoisCinemasESoOQueConfirmaFicaAtivo() throws Exception {
        perform(post("/api/v1/admin/movies/" + movie.getId() + "/offer"), adminToken,
                "{\"cinemaIds\":[" + cinemaA.getId() + "," + cinemaB.getId() + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO_GERENTE"));

        perform(get("/api/v1/manager/programacao"), managerBToken, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO_GERENTE"));
        perform(post("/api/v1/manager/programacao/" + movie.getId() + "/confirm"), managerBToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaB.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/v1/movies").param("cinemaId", String.valueOf(cinemaA.getId())))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        perform(get("/api/v1/admin/movies"), adminToken, null)
                .andExpect(jsonPath("$.content[0].cinemas", hasSize(2)));

        // Enviado e ainda não confirmado: pedir o mesmo filme não faz sentido.
        perform(post("/api/v1/manager/movie-requests"), managerAToken, "{\"tmdbId\":" + TMDB_ID + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void gerenteNaoConfirmaFilmeQueNaoFoiEnviadoAoSeuCinema() throws Exception {
        perform(post("/api/v1/admin/movies/" + movie.getId() + "/offer"), adminToken,
                "{\"cinemaIds\":[" + cinemaB.getId() + "]}")
                .andExpect(status().isOk());

        perform(post("/api/v1/manager/programacao/" + movie.getId() + "/confirm"), managerAToken, null)
                .andExpect(status().isNotFound());
        assertEquals(ProgramacaoStatus.AGUARDANDO_GERENTE,
                cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaB.getId(), movie.getId()).orElseThrow().getStatus());
    }

    @Test
    void cadaPapelSoAcessaAsPropriasRotas() throws Exception {
        String offer = "{\"cinemaIds\":[" + cinemaA.getId() + "]}";
        for (String token : List.of(managerAToken, staffToken, clientToken)) {
            perform(get("/api/v1/admin/movie-requests"), token, null).andExpect(status().isForbidden());
            perform(get("/api/v1/admin/movies"), token, null).andExpect(status().isForbidden());
            perform(post("/api/v1/admin/movies/" + movie.getId() + "/offer"), token, offer)
                    .andExpect(status().isForbidden());
        }
        for (String token : List.of(adminToken, staffToken, clientToken)) {
            perform(get("/api/v1/manager/programacao"), token, null).andExpect(status().isForbidden());
            perform(post("/api/v1/manager/movie-requests"), token, "{\"tmdbId\":" + TMDB_ID + "}")
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/manager/programacao")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/movie-requests")).andExpect(status().isUnauthorized());
    }

    private long createRequest(String managerToken) throws Exception {
        String body = perform(post("/api/v1/manager/movie-requests"), managerToken, "{\"tmdbId\":" + TMDB_ID + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_ADMIN"))
                .andExpect(jsonPath("$.titulo").value("Duna: Parte Dois"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asLong();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Cinema cinema(String nome, String cnpj) {
        Cinema cinema = new Cinema();
        cinema.setNome(nome);
        cinema.setCnpj(cnpj);
        cinema.setEndereco("Rua dos Pedidos, 1");
        return cinemaRepository.save(cinema);
    }

    private String token(String login, String role, Cinema cinema) {
        User user = new User();
        user.setNome("Pessoa " + login);
        user.setEmail(login + DOMAIN);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(TestRoles.ensure(roleRepository, role))));
        user.setCinema(cinema);
        return tokenService.generateToken(userRepository.save(user));
    }
}
