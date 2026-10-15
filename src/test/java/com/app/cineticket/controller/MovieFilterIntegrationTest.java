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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Filtros da vitrine: formato e áudio pelas sessões futuras, classificação pelo filme. */
class MovieFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String MOVIES = "/api/v1/movies";
    private static final String TITLES = "$.content[*].titulo";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema sp = testData.cinema("Cine Filtro SP", "44555666000177", "São Paulo", "SP");
        Cinema rj = testData.cinema("Cine Filtro RJ", "55666777000188", "Rio de Janeiro", "RJ");
        Room roomSp = testData.room("Sala SP", sp, 10);
        Room roomRj = testData.room("Sala RJ", rj, 10);

        Movie dublado3d = movie("Filme 3D Dublado", "12", sp);
        Movie legendado = movie("Filme Legendado", "14", sp);
        movie("Filme Livre", "L", sp);
        Movie soNoRio = movie("Filme 3D no Rio", "16", rj);

        session(dublado3d, roomSp, "3D", SessionAudio.DUBLADO, 1);
        session(legendado, roomSp, "2D", SessionAudio.LEGENDADO, 1);
        session(legendado, roomSp, "3D", SessionAudio.LEGENDADO, -1); // já passou: não conta
        session(soNoRio, roomRj, "3D", SessionAudio.DUBLADO, 1);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
    }

    @Test
    void semFiltroMostraTudoQueEstaEmCartazNaCidade() throws Exception {
        mockMvc.perform(get(MOVIES).param("cidade", "são paulo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TITLES, containsInAnyOrder("Filme 3D Dublado", "Filme Legendado", "Filme Livre")));
    }

    @Test
    void formatoEAudioOlhamSoAsSessoesFuturasDaCidade() throws Exception {
        mockMvc.perform(get(MOVIES).param("cidade", "São Paulo").param("formato", "3D"))
                .andExpect(jsonPath(TITLES, containsInAnyOrder("Filme 3D Dublado")));
        mockMvc.perform(get(MOVIES).param("cidade", "São Paulo").param("audio", "LEGENDADO"))
                .andExpect(jsonPath(TITLES, containsInAnyOrder("Filme Legendado")));
        mockMvc.perform(get(MOVIES).param("formato", "3D").param("audio", "DUBLADO"))
                .andExpect(jsonPath(TITLES, containsInAnyOrder("Filme 3D Dublado", "Filme 3D no Rio")));
    }

    @Test
    void classificacaoFiltraPeloFilme() throws Exception {
        mockMvc.perform(get(MOVIES).param("classificacao", "L"))
                .andExpect(jsonPath(TITLES, containsInAnyOrder("Filme Livre")));
    }

    @Test
    void filtroInvalidoRecebe400() throws Exception {
        mockMvc.perform(get(MOVIES).param("formato", "4D")).andExpect(status().isBadRequest());
        mockMvc.perform(get(MOVIES).param("classificacao", "13")).andExpect(status().isBadRequest());
    }

    private Movie movie(String titulo, String classificacao, Cinema cinema) {
        Movie movie = testData.movie(titulo, 100);
        movie.setClassificacao(classificacao);
        movie = movieRepository.save(movie);
        testData.program(cinema, movie, ProgramacaoStatus.ATIVO);
        return movie;
    }

    private void session(Movie movie, Room room, String formato, SessionAudio audio, int daysFromNow) {
        Session session = testData.session(movie, room, LocalDateTime.now().plusDays(daysFromNow).withHour(15));
        session.setFormato(formato);
        session.setAudio(audio);
        sessionRepository.save(session);
    }
}
