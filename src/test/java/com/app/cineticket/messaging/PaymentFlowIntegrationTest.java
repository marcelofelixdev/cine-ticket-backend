package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.app.cineticket.support.ApiRequests.authed;
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
    @Autowired private TestData testData;
    @Autowired private TicketRepository ticketRepository;

    private String token;
    private Session session;
    private Seat seat;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Pagamento", "33333333000133");
        Room room = testData.room("Sala P", cinema, 10);
        seat = testData.seat(room, "A", 1);
        Movie movie = testData.movie("Filme Pagamento", 100);
        session = testData.session(movie, room, LocalDateTime.now().plusDays(1), new BigDecimal("40.00"));
        token = testData.token(testData.customer(EMAIL));
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(EMAIL);
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
        String response = mockMvc.perform(authed(post("/api/v1/tickets"), token, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions pay(Long ticketId, String paymentMethodId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/tickets/" + ticketId + "/payment"), token, "{\"paymentMethodId\":\"" + paymentMethodId + "\"}"));
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
