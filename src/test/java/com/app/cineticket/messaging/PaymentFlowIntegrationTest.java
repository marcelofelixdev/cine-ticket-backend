package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo assíncrono completo com RabbitMQ real: a compra nasce PENDING, o evento é
 * publicado após o commit, o consumidor (simulador de pagamento do perfil dev) consome
 * a mensagem e o ingresso fica APPROVED.
 */
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "pagamento@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;

    private String token;
    private Session session;
    private Seat seat;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Pagamento");
        cinema.setCnpj("33333333000133");
        cinema.setEndereco("Rua B, 2");
        cinema = cinemaRepository.save(cinema);

        Room room = new Room();
        room.setNome("Sala P");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        seat = new Seat();
        seat.setFila("A");
        seat.setNumero(1);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        Movie movie = new Movie();
        movie.setTitulo("Filme Pagamento");
        movie.setDuracaoEmMinutos(100);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("40.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        User user = new User();
        user.setNome("Comprador");
        user.setEmail(EMAIL);
        user.setSenha("hash-irrelevante");
        token = tokenService.generateToken(userRepository.save(user));
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void compraPendenteEAprovadaPeloConsumidorDePagamento() throws Exception {
        String body = "{\"sessionId\":" + session.getId() + ",\"seatId\":" + seat.getId()
                + ",\"ticketType\":\"MEIA_ESTUDANTE\",\"cartaoToken\":\"tok_test\"}";

        String response = mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = ((Number) JsonPath.read(response, "$.id")).longValue();

        TicketStatus status = waitForStatusOtherThanPending(ticketId);

        assertEquals(TicketStatus.APPROVED, status);
        assertEquals(0, new BigDecimal("20.00").compareTo(
                ticketRepository.findById(ticketId).orElseThrow().getValorPago()));
    }

    /** Espera o consumidor processar a mensagem (até 15 s). */
    private TicketStatus waitForStatusOtherThanPending(Long ticketId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        TicketStatus status = TicketStatus.PENDING;
        while (status == TicketStatus.PENDING && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            status = ticketRepository.findById(ticketId).orElseThrow().getStatus();
        }
        return status;
    }
}
