package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exclusão de sala e de cinema contra o banco H2: os assentos têm FK para a sala
 * (e a sala para o cinema), então antes desta correção o DELETE de sala sempre falhava.
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
class RoomDeletionTest {

    @Autowired private RoomService roomService;
    @Autowired private CinemaService cinemaService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private MovieRepository movieRepository;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private Long cinemaId;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Teste");
        cinema.setCnpj("12345678000199");
        cinema.setEndereco("Rua A, 1");
        cinemaId = cinemaRepository.save(cinema).getId();
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
    void excluiSalaSemSessoesJuntoComOsAssentos() {
        Long roomId = roomService.create(new RoomRequestDTO("Sala 1", 25, cinemaId)).id();
        assertEquals(25, seatRepository.count());

        roomService.delete(roomId);

        assertFalse(roomRepository.existsById(roomId));
        assertEquals(0, seatRepository.count());
    }

    @Test
    void naoExcluiSalaComSessoes() {
        Long roomId = roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId)).id();
        criarSessao(roomRepository.findById(roomId).orElseThrow());

        assertThrows(ConflictException.class, () -> roomService.delete(roomId));

        assertTrue(roomRepository.existsById(roomId));
        assertEquals(10, seatRepository.count());
    }

    @Test
    void salaInexistenteRetornaNaoEncontrada() {
        assertThrows(ResourceNotFoundException.class, () -> roomService.delete(999_999L));
    }

    @Test
    void excluiCinemaSemSessoesJuntoComSalasEAssentos() {
        roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId));
        roomService.create(new RoomRequestDTO("Sala 2", 15, cinemaId));

        cinemaService.delete(cinemaId);

        assertFalse(cinemaRepository.existsById(cinemaId));
        assertEquals(0, roomRepository.count());
        assertEquals(0, seatRepository.count());
    }

    @Test
    void naoExcluiCinemaComSessoesEmAlgumaSala() {
        roomService.create(new RoomRequestDTO("Sala 1", 10, cinemaId));
        Long roomComSessao = roomService.create(new RoomRequestDTO("Sala 2", 10, cinemaId)).id();
        criarSessao(roomRepository.findById(roomComSessao).orElseThrow());

        assertThrows(ConflictException.class, () -> cinemaService.delete(cinemaId));

        assertTrue(cinemaRepository.existsById(cinemaId));
        assertEquals(2, roomRepository.count());
        assertEquals(20, seatRepository.count());
    }

    private void criarSessao(Room room) {
        Movie movie = new Movie();
        movie.setTitulo("Filme Teste");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);

        Session session = new Session();
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        session.setMovie(movie);
        session.setRoom(room);
        sessionRepository.save(session);
    }
}
