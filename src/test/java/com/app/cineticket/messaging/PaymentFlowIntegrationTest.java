package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo com RabbitMQ real e o gateway simulado (payments.provider=mock):
 * a compra reserva o assento (PENDING), o pagamento grava a confirmação no outbox,
 * o relay publica, o consumidor aprova e o ingresso fica APPROVED.
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
    void reservaPagaComCartaoDeTesteEAprovadaPeloConsumidor() throws Exception {
        Long ticketId = buy();

        pay(ticketId, "pm_card_visa")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        TicketStatus status = waitForStatusOtherThanPending(ticketId);

        assertEquals(TicketStatus.APPROVED, status);
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(ticket.getValorPago()));
        assertTrue(ticket.getPaymentIntentId().startsWith("pi_mock_"));
    }

    @Test
    void cartaoRecusadoRetorna422EMantemAReserva() throws Exception {
        Long ticketId = buy();

        pay(ticketId, "pm_card_chargeDeclined")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Pagamento recusado"));

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketStatus.PENDING, ticket.getStatus());
        assertNull(ticket.getPaymentIntentId());
    }

    @Test
    void segundoPagamentoDoMesmoIngressoRetorna409() throws Exception {
        Long ticketId = buy();
        pay(ticketId, "pm_card_visa").andExpect(status().isAccepted());

        pay(ticketId, "pm_card_mastercard").andExpect(status().isConflict());
    }

    private Long buy() throws Exception {
        String body = "{\"sessionId\":" + session.getId() + ",\"seatId\":" + seat.getId()
                + ",\"ticketType\":\"MEIA_ESTUDANTE\",\"documentoMeia\":\"CIE-2026-0001\"}";
        String response = mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions pay(Long ticketId, String paymentMethodId) throws Exception {
        return mockMvc.perform(post("/api/v1/tickets/" + ticketId + "/payment")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + paymentMethodId + "\"}"));
    }

    /** Espera o consumidor processar a mensagem (até 15 s). */
    private TicketStatus waitForStatusOtherThanPending(Long ticketId) {
        return await("ingresso " + ticketId + " sair de PENDING")
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> ticketRepository.findById(ticketId).orElseThrow().getStatus(),
                        status -> status != TicketStatus.PENDING);
    }
}
