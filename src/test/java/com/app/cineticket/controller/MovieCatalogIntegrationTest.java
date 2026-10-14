package com.app.cineticket.controller;

import com.app.cineticket.support.TestRoles;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.MovieCastMember;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ficha do filme na API, correção manual e acesso à busca no TMDB. Nenhum teste aqui
 * depende de o TMDB responder: as chamadas que chegariam nele não são feitas.
 */
class MovieCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@catalogo.teste";
    private static final String CNPJ = "43434343000143";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CinemaRepository cinemaRepository;

    private Long movieId;
    private String adminToken;
    private String managerToken;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        Movie movie = new Movie();
        movie.setTitulo("Duna: Parte Dois");
        movie.setDuracaoEmMinutos(167);
        movie.setDiretor("Denis Villeneuve");
        movie.setGeneros("Ficção científica, Aventura");
        movie.setClassificacao("14");
        movie.setTrailerYoutubeKey("abc123");
        movie.setDataEstreia(LocalDate.of(2024, 2, 29));
        movie.getElenco().add(new MovieCastMember("Timothée Chalamet", "Paul Atreides"));
        movie.getElenco().add(new MovieCastMember("Zendaya", "Chani"));
        movieId = movieRepository.save(movie).getId();

        Cinema cinema = new Cinema();
        cinema.setNome("Cine Catálogo");
        cinema.setCnpj(CNPJ);
        cinema.setEndereco("Rua do Catálogo, 1");
        cinema = cinemaRepository.save(cinema);

        adminToken = token("admin", "ROLE_ADMIN", null);
        managerToken = token("gerente", "ROLE_MANAGER", cinema);
        staffToken = token("funcionario", "ROLE_STAFF", cinema);
        clientToken = token("cliente", "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        movieRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(DOMAIN))
                .toList());
        cinemaRepository.findAll().stream()
                .filter(cinema -> CNPJ.equals(cinema.getCnpj()))
                .forEach(cinemaRepository::delete);
    }

    @Test
    void fichaCompletaNoCatalogoPublico() throws Exception {
        mockMvc.perform(get("/api/v1/movies/" + movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diretor").value("Denis Villeneuve"))
                .andExpect(jsonPath("$.generos[0]").value("Ficção científica"))
                .andExpect(jsonPath("$.generos[1]").value("Aventura"))
                .andExpect(jsonPath("$.classificacao").value("14"))
                .andExpect(jsonPath("$.trailerUrl").value("https://www.youtube.com/watch?v=abc123"))
                .andExpect(jsonPath("$.dataEstreia").value("2024-02-29"))
                .andExpect(jsonPath("$.elenco[0].nome").value("Timothée Chalamet"))
                .andExpect(jsonPath("$.elenco[1].personagem").value("Chani"));

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].elenco.length()").value(2));
    }

    @Test
    void adminCorrigeSoOsCamposEnviados() throws Exception {
        mockMvc.perform(put("/api/v1/movies/" + movieId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classificacao\":\"16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacao").value("16"))
                .andExpect(jsonPath("$.titulo").value("Duna: Parte Dois"))
                .andExpect(jsonPath("$.duracaoEmMinutos").value(167))
                .andExpect(jsonPath("$.elenco.length()").value(2));
    }

    @Test
    void classificacaoInvalidaRecebe400() throws Exception {
        mockMvc.perform(put("/api/v1/movies/" + movieId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classificacao\":\"18+\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroSemTmdbIdExigeTituloEDuracao() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Só o título\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sincronizarFilmeSemTmdbRecebe422() throws Exception {
        mockMvc.perform(post("/api/v1/movies/" + movieId + "/tmdb-sync")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sincronizarESoDoAdmin() throws Exception {
        for (String token : List.of(managerToken, staffToken, clientToken)) {
            mockMvc.perform(post("/api/v1/movies/" + movieId + "/tmdb-sync")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void buscaNoTmdbESoDeAdminEGerente() throws Exception {
        mockMvc.perform(get("/api/v1/tmdb/search").param("q", "duna")).andExpect(status().isUnauthorized());
        for (String token : List.of(staffToken, clientToken)) {
            mockMvc.perform(get("/api/v1/tmdb/search").param("q", "duna")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        // Busca curta demais é recusada antes de chegar no TMDB.
        for (String token : List.of(adminToken, managerToken)) {
            mockMvc.perform(get("/api/v1/tmdb/search").param("q", " a ")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
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
