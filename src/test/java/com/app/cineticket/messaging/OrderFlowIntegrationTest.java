package com.app.cineticket.messaging;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.TicketOrder;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.OrderStatus;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.TicketOrderRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.service.PendingTicketExpirationJob;
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
import java.util.List;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compra por pedido com RabbitMQ real e o gateway simulado: vários lugares reservados juntos
 * (todos ou nenhum), uma cobrança só e a aprovação de todos os ingressos pelo consumidor.
 */
class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "pedido@teste.com";
    private static final String OTHER = "outro-pedido@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketOrderRepository orderRepository;
    @Autowired private PendingTicketExpirationJob expirationJob;

    private User buyer;
    private String token;
    private Session session;
    private List<Seat> seats;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Pedido", "34343434000134");
        Room room = testData.room("Sala Pedido", cinema, 10);
        seats = testData.seats(room, "A", 6);
        Movie movie = testData.movie("Filme Pedido", 100);
        session = testData.session(movie, room, LocalDateTime.now().plusDays(1), new BigDecimal("40.00"));
        buyer = testData.customer(EMAIL);
        token = testData.token(buyer);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(EMAIL, OTHER);
    }

    @Test
    void pedidoReservaOsLugaresJuntosEUmaCobrancaAprovaTodos() throws Exception {
        String body = order(item(seats.get(0), "INTEIRA", null), item(seats.get(1), "MEIA_ESTUDANTE", "CIE-2026-0001"));
        String created = mockMvc.perform(authed(post("/api/v1/orders"), token, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(60.00))
                .andExpect(jsonPath("$.ingressos.length()").value(2))
                .andExpect(jsonPath("$.ingressos[0].assento").value("A-1"))
                .andExpect(jsonPath("$.ingressos[1].valor").value(20.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(created, "$.id")).longValue();

        pay(orderId, "pm_card_visa").andExpect(status().isAccepted());

        await("pedido " + orderId + " pago").atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .until(() -> orderRepository.findById(orderId).orElseThrow().getStatus(), s -> s == OrderStatus.PAID);
        TicketOrder order = orderRepository.findById(orderId).orElseThrow();
        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(buyer.getId()))
                .toList();
        assertEquals(2, tickets.size());
        tickets.forEach(ticket -> {
            assertEquals(TicketStatus.APPROVED, ticket.getStatus());
            assertEquals(order.getPaymentIntentId(), ticket.getPaymentIntentId());
        });
        assertTrue(order.getPaymentIntentId().startsWith("pi_mock_"));

        mockMvc.perform(authed(get("/api/v1/orders/" + orderId), token, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.ingressos[0].status").value("APPROVED"));
    }

    @Test
    void lugarOcupadoNoMeioDoPedidoNaoReservaNenhum() throws Exception {
        testData.ticket(session, seats.get(1), testData.customer(OTHER), TicketStatus.APPROVED);

        mockMvc.perform(authed(post("/api/v1/orders"), token,
                        order(item(seats.get(0), "INTEIRA", null), item(seats.get(1), "INTEIRA", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A cadeira A-2 já está ocupada."));

        assertEquals(0, ticketRepository.countByUserIdAndStatus(buyer.getId(), TicketStatus.PENDING));
        assertEquals(0, orderRepository.count());
    }

    @Test
    void naoPassaDoLimiteDeLugaresPorPedidoQueOMapaInforma() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/seats"))
                .andExpect(jsonPath("$.maxLugaresPorPedido").value(4));

        String cinco = order(item(seats.get(0), "INTEIRA", null), item(seats.get(1), "INTEIRA", null),
                item(seats.get(2), "INTEIRA", null), item(seats.get(3), "INTEIRA", null), item(seats.get(4), "INTEIRA", null));
        mockMvc.perform(authed(post("/api/v1/orders"), token, cinco))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Escolha no máximo 4 lugares por compra."));
    }

    @Test
    void pedidoDeOutraPessoaDa403() throws Exception {
        long orderId = createOrder();
        String outro = testData.token(testData.customer(OTHER));

        mockMvc.perform(authed(get("/api/v1/orders/" + orderId), outro, null)).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/payment"), outro, "{\"paymentMethodId\":\"pm_card_visa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pedidoVencidoNaoAceitaPagamentoEExpiraComOsIngressos() throws Exception {
        long orderId = createOrder();
        TicketOrder order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);
        ticketRepository.findAll().forEach(ticket -> {
            ticket.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            ticketRepository.save(ticket);
        });

        pay(orderId, "pm_card_visa").andExpect(status().isConflict());

        expirationJob.expirePendingTickets();
        assertEquals(OrderStatus.EXPIRED, orderRepository.findById(orderId).orElseThrow().getStatus());
        ticketRepository.findAll().forEach(ticket -> assertEquals(TicketStatus.EXPIRED, ticket.getStatus()));
    }

    private long createOrder() throws Exception {
        String created = mockMvc.perform(authed(post("/api/v1/orders"), token, order(item(seats.get(0), "INTEIRA", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private ResultActions pay(long orderId, String paymentMethodId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/payment"), token,
                "{\"paymentMethodId\":\"" + paymentMethodId + "\"}"));
    }

    private String order(String... itens) {
        return "{\"sessionId\":" + session.getId() + ",\"itens\":[" + String.join(",", itens) + "]}";
    }

    private static String item(Seat seat, String type, String documento) {
        return "{\"seatId\":" + seat.getId() + ",\"ticketType\":\"" + type + "\""
                + (documento == null ? "" : ",\"documentoMeia\":\"" + documento + "\"") + "}";
    }
}
