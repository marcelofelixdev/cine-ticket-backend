package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Refund;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reembolso ponta a ponta: cancelamento → outbox → RabbitMQ → RefundConsumer → tb_refund. */
class RefundIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reembolso@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private OutboxService outboxService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefundRepository refundRepository;

    private Session session;
    private Seat seat;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Reembolso");
        cinema.setCnpj("55555555000155");
        cinema.setEndereco("Rua R, 5");
        cinema = cinemaRepository.save(cinema);

        Room room = new Room();
        room.setNome("Sala R");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        seat = new Seat();
        seat.setFila("A");
        seat.setNumero(1);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        Movie movie = new Movie();
        movie.setTitulo("Filme Reembolso");
        movie.setDuracaoEmMinutos(100);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("40.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        user = new User();
        user.setNome("Cliente");
        user.setEmail(EMAIL);
        user.setSenha("hash-irrelevante");
        user = userRepository.save(user);
        token = tokenService.generateToken(user);
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
    void cancelarIngressoPagoEstornaOPagamento() throws Exception {
        String paymentIntentId = "pi_mock_" + UUID.randomUUID();
        Ticket ticket = approvedTicket(paymentIntentId);

        mockMvc.perform(put("/api/v1/tickets/" + ticket.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Refund refund = waitForRefund(paymentIntentId);
        assertEquals(RefundStatus.REFUNDED, refund.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(refund.getAmount()));

        mockMvc.perform(get("/api/v1/tickets/" + ticket.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.reembolso").value("REFUNDED"));
    }

    @Test
    void pedidoDuplicadoGeraUmUnicoEstorno() throws Exception {
        String paymentIntentId = "pi_mock_" + UUID.randomUUID();
        Ticket ticket = approvedTicket(paymentIntentId);
        RefundEventDTO event = new RefundEventDTO(ticket.getId(), paymentIntentId, new BigDecimal("40.00"));

        transactionTemplate.executeWithoutResult(status -> outboxService.refundRequested(event));
        transactionTemplate.executeWithoutResult(status -> outboxService.refundRequested(event));

        waitForRefund(paymentIntentId);
        Thread.sleep(3_000); // tempo para a segunda mensagem também ser consumida

        List<Refund> refunds = refundRepository.findByTicketId(ticket.getId());
        assertEquals(1, refunds.size());
        assertEquals(1, refunds.get(0).getAttempts());
    }

    private Ticket approvedTicket(String paymentIntentId) {
        Ticket ticket = new Ticket();
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setValorPago(new BigDecimal("40.00"));
        ticket.setStatus(TicketStatus.APPROVED);
        ticket.setPaymentIntentId(paymentIntentId);
        return ticketRepository.save(ticket);
    }

    private Refund waitForRefund(String paymentIntentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            var refund = refundRepository.findByPaymentIntentId(paymentIntentId);
            if (refund.isPresent() && refund.get().getStatus() == RefundStatus.REFUNDED) {
                return refund.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("O pagamento " + paymentIntentId + " não foi estornado a tempo");
    }
}
