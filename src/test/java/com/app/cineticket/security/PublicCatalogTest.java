package com.app.cineticket.security;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catálogo público (item 11): visitantes consultam filmes, cinemas e sessões sem login,
 * mas salas, ingressos e qualquer escrita continuam protegidos.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "api.security.token.secret=test_secret_for_ci_with_at_least_32_chars",
        "TMDB_API_KEY=test_tmdb_key",
        "app.bootstrap.admin.enabled=false"
})
@AutoConfigureMockMvc
class PublicCatalogTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private Long cinemaId;
    private Long movieId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Teste");
        cinema.setCnpj("12345678000199");
        cinema.setEndereco("Rua A, 1");
        cinema = cinemaRepository.save(cinema);
        cinemaId = cinema.getId();

        Room room = new Room();
        room.setNome("Sala 1");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        Movie movie = new Movie();
        movie.setTitulo("Filme Teste");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);
        movieId = movie.getId();

        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        sessionId = sessionRepository.save(session).getId();
    }

    @AfterEach
    void cleanUp() {
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
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
