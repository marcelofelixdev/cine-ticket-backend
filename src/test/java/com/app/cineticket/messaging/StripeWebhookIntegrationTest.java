package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.OutboxEvent;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.OutboxEventRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook do Stripe ponta a ponta: assinatura (calculada aqui como o Stripe faz),
 * deduplicação, outbox, RabbitMQ e consumidor.
 */
class StripeWebhookIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK = "/api/v1/payments/webhook";
    private static final String EMAIL = "webhook@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private com.app.cineticket.repository.RefundRepository refundRepository;
    @Autowired private com.app.cineticket.repository.TicketOrderRepository orderRepository;

    private Session session;
    private Seat seat;
    private User user;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Webhook", "44444444000144");
        Room room = testData.room("Sala W", cinema, 10);
        seat = testData.seat(room, "A", 1);
        Movie movie = testData.movie("Filme Webhook", 100);
        session = testData.session(movie, room, LocalDateTime.now().plusDays(1), new BigDecimal("40.00"));
        user = testData.customer(EMAIL);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(EMAIL);
    }

    @Test
    void semAssinaturaRetorna400() throws Exception {
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Assinatura do webhook inválida"));
    }

    @Test
    void assinaturaComOutroSegredoRetorna400() throws Exception {
        Ticket ticket = ticket(TicketStatus.PENDING);
        String payload = succeededEvent("evt_" + UUID.randomUUID(), ticket, "pi_forjado");

        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sign(payload, "whsec_de_um_atacante"))
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertEquals(TicketStatus.PENDING, ticketRepository.findById(ticket.getId()).orElseThrow().getStatus());
    }

    @Test
    void pagamentoConfirmadoAprovaOIngresso() throws Exception {
        Ticket ticket = ticket(TicketStatus.PENDING);
        String paymentIntentId = "pi_ok_" + UUID.randomUUID();

        send(succeededEvent("evt_" + UUID.randomUUID(), ticket, paymentIntentId)).andExpect(status().isOk());

        Ticket approved = waitForStatus(ticket.getId(), TicketStatus.APPROVED);
        assertEquals(paymentIntentId, approved.getPaymentIntentId());
    }

    @Test
    void eventoRepetidoEProcessadoUmaVezSo() throws Exception {
        Ticket ticket = ticket(TicketStatus.PENDING);
        String payload = succeededEvent("evt_" + UUID.randomUUID(), ticket, "pi_dup_" + UUID.randomUUID());

        send(payload).andExpect(status().isOk());
        send(payload).andExpect(status().isOk());

        waitForStatus(ticket.getId(), TicketStatus.APPROVED);
        assertEquals(1, outboxEvents(ticket.getId(), "PaymentConfirmed").size());
    }

    @Test
    void pagamentoDeIngressoExpiradoPedeReembolso() throws Exception {
        Ticket ticket = ticket(TicketStatus.EXPIRED);
        String paymentIntentId = "pi_late_" + UUID.randomUUID();

        send(succeededEvent("evt_" + UUID.randomUUID(), ticket, paymentIntentId)).andExpect(status().isOk());

        List<OutboxEvent> refunds = waitForOutbox(ticket.getId(), "RefundRequested");
        assertTrue(refunds.get(0).getPayload().contains(paymentIntentId), refunds.get(0).getPayload());
        assertEquals(TicketStatus.EXPIRED, ticketRepository.findById(ticket.getId()).orElseThrow().getStatus());

        // O RefundConsumer estorna o pagamento (pelo gateway simulado, nos testes).
        await("estorno do pagamento atrasado")
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> refundRepository.findByPaymentIntentId(paymentIntentId)
                        .filter(r -> r.getStatus() == com.app.cineticket.domain.enums.RefundStatus.REFUNDED)
                        .isPresent());
    }

    @Test
    void pagamentoDePedidoAprovaTodosOsIngressosDoPedido() throws Exception {
        com.app.cineticket.domain.entity.TicketOrder order = new com.app.cineticket.domain.entity.TicketOrder();
        order.setUser(user);
        order.setSession(session);
        order.setStatus(com.app.cineticket.domain.enums.OrderStatus.PENDING);
        order.setTotal(new BigDecimal("40.00"));
        order.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        order = orderRepository.save(order);
        Ticket ticket = ticket(TicketStatus.PENDING);
        ticket.setTicketOrder(order);
        ticketRepository.save(ticket);
        String paymentIntentId = "pi_order_" + UUID.randomUUID();
        String payload = """
                {"id":"evt_%s","object":"event","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","amount":4000,
                 "amount_received":4000,"currency":"brl","status":"succeeded",
                 "metadata":{"orderId":"%d"}}}}
                """.formatted(UUID.randomUUID(), paymentIntentId, order.getId());

        send(payload).andExpect(status().isOk());

        Ticket approved = waitForStatus(ticket.getId(), TicketStatus.APPROVED);
        assertEquals(paymentIntentId, approved.getPaymentIntentId());
        assertEquals(com.app.cineticket.domain.enums.OrderStatus.PAID,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    private Ticket ticket(TicketStatus status) {
        return testData.ticket(session, seat, user, status);
    }

    /** Evento no formato do Stripe; o valor vem em centavos. */
    private static String succeededEvent(String eventId, Ticket ticket, String paymentIntentId) {
        return """
                {"id":"%s","object":"event","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","amount":4000,
                 "amount_received":4000,"currency":"brl","status":"succeeded",
                 "metadata":{"ticketId":"%d"}}}}
                """.formatted(eventId, paymentIntentId, ticket.getId());
    }

    private ResultActions send(String payload) throws Exception {
        return mockMvc.perform(post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET))
                .content(payload));
    }

    /** Mesmo esquema do Stripe: v1 = HMAC-SHA256(secret, "timestamp.payload"). */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
    }

    private List<OutboxEvent> outboxEvents(Long ticketId, String eventType) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(String.valueOf(ticketId)))
                .filter(e -> e.getEventType().equals(eventType))
                .toList();
    }

    private List<OutboxEvent> waitForOutbox(Long ticketId, String eventType) {
        return await(eventType + " para o ingresso " + ticketId)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> outboxEvents(ticketId, eventType), events -> !events.isEmpty());
    }

    private Ticket waitForStatus(Long ticketId, TicketStatus expected) {
        return await("ingresso " + ticketId + " em " + expected)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> ticketRepository.findById(ticketId).orElseThrow(), ticket -> ticket.getStatus() == expected);
    }
}
