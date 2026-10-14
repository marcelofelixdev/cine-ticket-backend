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
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reembolso ponta a ponta: cancelamento → outbox → RabbitMQ → RefundConsumer → tb_refund. */
class RefundIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reembolso@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private TokenService tokenService;
    @Autowired private OutboxService outboxService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private RefundRepository refundRepository;

    private Session session;
    private Seat seat;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Reembolso", "55555555000155");
        Room room = testData.room("Sala R", cinema, 10);
        seat = testData.seat(room, "A", 1);
        Movie movie = testData.movie("Filme Reembolso", 100);
        session = testData.session(movie, room, LocalDateTime.now().plusDays(1), new BigDecimal("40.00"));
        user = testData.customer(EMAIL);
        token = testData.token(user);
        token = tokenService.generateToken(user);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(EMAIL);
    }

    @Test
    void cancelarIngressoPagoEstornaOPagamento() throws Exception {
        String paymentIntentId = "pi_mock_" + UUID.randomUUID();
        Ticket ticket = approvedTicket(paymentIntentId);

        mockMvc.perform(authed(put("/api/v1/tickets/" + ticket.getId() + "/cancel"), token, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Refund refund = waitForRefund(paymentIntentId);
        assertEquals(RefundStatus.REFUNDED, refund.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(refund.getAmount()));

        mockMvc.perform(authed(get("/api/v1/tickets/" + ticket.getId()), token, null))
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

        // A segunda mensagem também é consumida; durante 3 s continua existindo um único estorno.
        await("um único estorno para " + paymentIntentId)
                .during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> refundRepository.findByTicketId(ticket.getId()),
                        refunds -> refunds.size() == 1 && refunds.get(0).getAttempts() == 1);
    }

    private Ticket approvedTicket(String paymentIntentId) {
        Ticket ticket = testData.ticket(session, seat, user, TicketStatus.APPROVED);
        ticket.setPaymentIntentId(paymentIntentId);
        return ticketRepository.save(ticket);
    }

    private Refund waitForRefund(String paymentIntentId) {
        return await("estorno do pagamento " + paymentIntentId)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> refundRepository.findByPaymentIntentId(paymentIntentId),
                        refund -> refund.isPresent() && refund.get().getStatus() == RefundStatus.REFUNDED)
                .orElseThrow();
    }
}
