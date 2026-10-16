package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.MovieCastMember;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.repository.MovieRepository;
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
import static org.hamcrest.Matchers.nullValue;
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
    @Autowired private TestData testData;
    @Autowired private MovieRepository movieRepository;

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
        movie.getElenco().add(new MovieCastMember("Timothée Chalamet", "Paul Atreides",
                "https://image.tmdb.org/t/p/w185/chalamet.jpg"));
        movie.getElenco().add(new MovieCastMember("Zendaya", "Chani"));
        movie = movieRepository.save(movie);
        movieId = movie.getId();

        Cinema cinema = testData.cinema("Cine Catálogo", CNPJ);
        testData.program(cinema, movie, ProgramacaoStatus.ATIVO);

        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        managerToken = testData.token("gerente" + DOMAIN, "ROLE_MANAGER", cinema);
        staffToken = testData.token("funcionario" + DOMAIN, "ROLE_STAFF", cinema);
        clientToken = testData.token("cliente" + DOMAIN, "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
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
                .andExpect(jsonPath("$.elenco[0].fotoUrl").value("https://image.tmdb.org/t/p/w185/chalamet.jpg"))
                .andExpect(jsonPath("$.elenco[1].personagem").value("Chani"))
                .andExpect(jsonPath("$.elenco[1].fotoUrl").value(nullValue()));

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].elenco.length()").value(2));
    }

    @Test
    void adminCorrigeSoOsCamposEnviados() throws Exception {
        mockMvc.perform(authed(put("/api/v1/movies/" + movieId), adminToken, "{\"classificacao\":\"16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacao").value("16"))
                .andExpect(jsonPath("$.titulo").value("Duna: Parte Dois"))
                .andExpect(jsonPath("$.duracaoEmMinutos").value(167))
                .andExpect(jsonPath("$.elenco.length()").value(2));
    }

    @Test
    void classificacaoInvalidaRecebe422() throws Exception {
        mockMvc.perform(authed(put("/api/v1/movies/" + movieId), adminToken, "{\"classificacao\":\"18+\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("classificacao"));
    }

    @Test
    void cadastroSemTmdbIdExigeTituloEDuracao() throws Exception {
        mockMvc.perform(authed(post("/api/v1/movies"), adminToken, "{\"titulo\":\"Só o título\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("identificado"));
    }

    @Test
    void sincronizarFilmeSemTmdbRecebe422() throws Exception {
        mockMvc.perform(authed(post("/api/v1/movies/" + movieId + "/tmdb-sync"), adminToken, null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sincronizarESoDoAdmin() throws Exception {
        for (String token : List.of(managerToken, staffToken, clientToken)) {
            mockMvc.perform(authed(post("/api/v1/movies/" + movieId + "/tmdb-sync"), token, null))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void buscaNoTmdbESoDeAdminEGerente() throws Exception {
        mockMvc.perform(get("/api/v1/tmdb/search").param("q", "duna")).andExpect(status().isUnauthorized());
        for (String token : List.of(staffToken, clientToken)) {
            mockMvc.perform(authed(get("/api/v1/tmdb/search").param("q", "duna"), token, null))
                    .andExpect(status().isForbidden());
        }
        // Busca curta demais é recusada antes de chegar no TMDB.
        for (String token : List.of(adminToken, managerToken)) {
            mockMvc.perform(authed(get("/api/v1/tmdb/search").param("q", " a "), token, null))
                    .andExpect(status().isBadRequest());
        }
    }
}
