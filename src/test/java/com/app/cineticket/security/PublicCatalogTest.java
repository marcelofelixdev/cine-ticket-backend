package com.app.cineticket.security;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catálogo público (item 11): visitantes consultam filmes, cinemas e sessões sem login,
 * mas salas, ingressos e qualquer escrita continuam protegidos.
 */
class PublicCatalogTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private Long cinemaId;
    private Long movieId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Teste", "12345678000199");
        cinemaId = cinema.getId();
        Room room = testData.room("Sala 1", cinema, 10);
        Movie movie = testData.movie("Filme Teste", 120);
        movieId = movie.getId();
        sessionId = testData.session(movie, room, LocalDateTime.now().plusDays(1)).getId();
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
    }

    @Test
    void visitanteConsultaOCatalogoSemLogin() throws Exception {
        mockMvc.perform(get("/api/v1/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/movies/" + movieId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cinemas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cinemas/" + cinemaId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/seats")).andExpect(status().isOk());
    }

    @Test
    void salasEIngressosContinuamExigindoLogin() throws Exception {
        mockMvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tickets/my-tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void escritaNoCatalogoContinuaExigindoLogin() throws Exception {
        mockMvc.perform(post("/api/v1/cinemas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
