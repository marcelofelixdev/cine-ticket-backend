package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
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

/**
 * Roda a query real de expiração (JPQL em lote) contra o banco H2.
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
class PendingTicketExpirationJobTest {

    @Autowired private PendingTicketExpirationJob job;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private Session session;
    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Teste");
        cinema.setCnpj("12345678000199");
        cinema.setEndereco("Rua A, 1");
        cinema = cinemaRepository.save(cinema);

        room = new Room();
        room.setNome("Sala 1");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        Movie movie = new Movie();
        movie.setTitulo("Filme Teste");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        session.setMovie(movie);
        session.setRoom(room);
        session = sessionRepository.save(session);

        user = new User();
        user.setNome("Cliente");
        user.setEmail("cliente-expiracao@teste.com");
        user.setSenha("hash-irrelevante");
        user = userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.findByEmailIgnoreCase("cliente-expiracao@teste.com").ifPresent(userRepository::delete);
    }

    @Test
    void expiraSomenteIngressosPendentesComPrazoVencido() {
        LocalDateTime agora = LocalDateTime.now();
        Long vencido = ticket(1, TicketStatus.PENDING, agora.minusMinutes(1));
        Long noPrazo = ticket(2, TicketStatus.PENDING, agora.plusMinutes(5));
        Long aprovado = ticket(3, TicketStatus.APPROVED, agora.minusMinutes(1));

        job.expirePendingTickets();

        assertEquals(TicketStatus.EXPIRED, statusOf(vencido));
        assertEquals(TicketStatus.PENDING, statusOf(noPrazo));
        assertEquals(TicketStatus.APPROVED, statusOf(aprovado));
    }

    private Long ticket(int seatNumber, TicketStatus status, LocalDateTime expiresAt) {
        Seat seat = new Seat();
        seat.setFila("A");
        seat.setNumero(seatNumber);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        Ticket ticket = new Ticket();
        ticket.setStatus(status);
        ticket.setValorPago(new BigDecimal("30.00"));
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setUser(user);
        ticket.setExpiresAt(expiresAt);
        return ticketRepository.save(ticket).getId();
    }

    private TicketStatus statusOf(Long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getStatus();
    }
}
