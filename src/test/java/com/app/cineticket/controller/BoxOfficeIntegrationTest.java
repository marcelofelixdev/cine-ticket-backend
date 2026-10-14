package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.BoxOfficeSaleRequestDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.service.BoxOfficeService;
import com.app.cineticket.service.TicketQrCodeService;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Venda no caixa: ingressos aprovados na hora, no próprio cinema, com as mesmas travas da compra online. */
class BoxOfficeIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@caixa.teste";
    private static final String CNPJ_A = "49494949000149";
    private static final String CNPJ_B = "50505050000150";
    private static final String SALES = "/api/v1/box-office/sales";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestData testData;
    @Autowired private BoxOfficeService boxOfficeService;
    @Autowired private TicketQrCodeService qrCodeService;
    @Autowired private TicketRepository ticketRepository;

    private Movie movie;
    private Room roomA;
    private List<Seat> seatsA;
    private Session sessionA;
    private Session sessionB;
    private User staff;
    private String staffToken;
    private String managerToken;
    private String adminToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinemaA = testData.cinema("Cine Caixa A", CNPJ_A);
        Cinema cinemaB = testData.cinema("Cine Caixa B", CNPJ_B);
        roomA = testData.room("Sala Caixa A", cinemaA, 10);
        Room roomB = testData.room("Sala Caixa B", cinemaB, 10);
        seatsA = testData.seats(roomA, "A", 10);
        testData.seat(roomB, "A", 1);
        movie = testData.movie("Filme do Caixa", 120);
        sessionA = testData.session(movie, roomA, LocalDateTime.now().plusDays(1));
        sessionB = testData.session(movie, roomB, LocalDateTime.now().plusDays(1));

        staff = testData.user("caixa" + DOMAIN, "ROLE_STAFF", cinemaA);
        staffToken = testData.token(staff);
        managerToken = testData.token("gerente" + DOMAIN, "ROLE_MANAGER", cinemaA);
        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        clientToken = testData.token("cliente" + DOMAIN, "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
    }

    @Test
    void vendaNoCaixaSaiAprovadaComTotalEQr() throws Exception {
        String body = sale(sessionA, "DINHEIRO", null,
                item(seatsA.get(0), "INTEIRA", null), item(seatsA.get(1), "MEIA_ESTUDANTE", "cie123456"));

        String response = mockMvc.perform(authed(post(SALES), staffToken, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(45.00))
                .andExpect(jsonPath("$.vinculadoAConta").value(false))
                .andExpect(jsonPath("$.ingressos", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(response).get("ingressos").get(0);
        assertEquals(qrCodeService.payloadFor(first.get("ticketId").asLong()), first.get("qr").asText());
        var ticket = ticketRepository.findById(first.get("ticketId").asLong()).orElseThrow();
        assertEquals(TicketStatus.APPROVED, ticket.getStatus());
        assertEquals(TicketChannel.BALCAO, ticket.getCanal());
        assertEquals(FormaPagamento.DINHEIRO, ticket.getFormaPagamento());
        assertNull(ticket.getUser());

        mockMvc.perform(authed(get("/api/v1/box-office/sessions")
                        .param("date", sessionA.getHorarioInicio().toLocalDate().toString()), staffToken, null))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ocupados").value(2));
    }

    @Test
    void lugarOcupadoBarraAVendaInteira() throws Exception {
        mockMvc.perform(authed(post(SALES), staffToken, sale(sessionA, "PIX", null, item(seatsA.get(1), "INTEIRA", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post(SALES), managerToken, sale(sessionA, "CREDITO", null,
                        item(seatsA.get(0), "INTEIRA", null), item(seatsA.get(1), "INTEIRA", null))))
                .andExpect(status().isConflict());

        assertEquals(1, ticketRepository.count(), "a venda com lugar ocupado não pode deixar ingresso para trás");
    }

    @Test
    void cotaDeMeiaContaOsLugaresDaVenda() throws Exception {
        // Sala de 10 lugares: cota de 4 meias.
        mockMvc.perform(authed(post(SALES), staffToken, sale(sessionA, "DEBITO", null,
                        item(seatsA.get(0), "MEIA_IDOSO", null), item(seatsA.get(1), "MEIA_IDOSO", null),
                        item(seatsA.get(2), "MEIA_IDOSO", null), item(seatsA.get(3), "MEIA_IDOSO", null),
                        item(seatsA.get(4), "MEIA_IDOSO", null))))
                .andExpect(status().isConflict());
        assertEquals(0, ticketRepository.count());
    }

    @Test
    void sessaoDeOutroCinemaRecebe403() throws Exception {
        mockMvc.perform(authed(post(SALES), staffToken, sale(sessionB, "DINHEIRO", null, item(seatsA.get(0), "INTEIRA", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendaEncerradaMeiaHoraDepoisDoInicio() throws Exception {
        Session started = testData.session(movie, roomA, LocalDateTime.now().minusMinutes(40));

        mockMvc.perform(authed(post(SALES), staffToken, sale(started, "DINHEIRO", null, item(seatsA.get(0), "INTEIRA", null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailDeContaLevaOIngressoParaMeusIngressosMasNaoCancelaOnline() throws Exception {
        String body = sale(sessionA, "CREDITO", "cliente" + DOMAIN, item(seatsA.get(0), "INTEIRA", null));
        String response = mockMvc.perform(authed(post(SALES), staffToken, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vinculadoAConta").value(true))
                .andReturn().getResponse().getContentAsString();
        long ticketId = objectMapper.readTree(response).get("ingressos").get(0).get("ticketId").asLong();

        mockMvc.perform(authed(get("/api/v1/tickets/my-tickets"), clientToken, null))
                .andExpect(jsonPath("$.content[0].id").value(ticketId))
                .andExpect(jsonPath("$.content[0].canal").value("BALCAO"));
        mockMvc.perform(authed(put("/api/v1/tickets/" + ticketId + "/cancel"), clientToken, null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ingressoSemDonoEntraNaSalaEImprimeNoCaixa() throws Exception {
        Session soon = testData.session(movie, roomA, LocalDateTime.now().plusMinutes(20));
        String response = mockMvc.perform(authed(post(SALES), staffToken,
                        sale(soon, "DINHEIRO", null, item(seatsA.get(0), "INTEIRA", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode ingresso = objectMapper.readTree(response).get("ingressos").get(0);

        mockMvc.perform(authed(get("/api/v1/box-office/tickets/" + ingresso.get("ticketId").asLong() + "/pdf"), staffToken, null))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(authed(post("/api/v1/tickets/validate"), staffToken, "{\"qr\":\"" + ingresso.get("qr").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void vendasSimultaneasDoMesmoLugarVendemUmaSoVez() throws Exception {
        BoxOfficeSaleRequestDTO request = new BoxOfficeSaleRequestDTO(sessionA.getId(),
                List.of(new BoxOfficeSaleRequestDTO.Item(seatsA.get(0).getId(), TicketType.INTEIRA, null)),
                FormaPagamento.DINHEIRO, null);
        int sellers = 4;
        CountDownLatch ready = new CountDownLatch(sellers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(sellers);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < sellers; i++) {
            results.add(pool.submit(attempt(request, ready, start)));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        int sold = 0;
        for (Future<Boolean> result : results) {
            if (Boolean.TRUE.equals(result.get())) {
                sold++;
            }
        }
        assertEquals(1, sold);
        assertEquals(1, ticketRepository.count());
    }

    @Test
    void soEquipeDoCinemaUsaOCaixa() throws Exception {
        String body = sale(sessionA, "DINHEIRO", null, item(seatsA.get(0), "INTEIRA", null));
        for (String token : List.of(adminToken, clientToken)) {
            mockMvc.perform(authed(post(SALES), token, body)).andExpect(status().isForbidden());
            mockMvc.perform(authed(get("/api/v1/box-office/sessions"), token, null)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(SALES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private Callable<Boolean> attempt(BoxOfficeSaleRequestDTO request, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                boxOfficeService.sell(request, staff);
                return true;
            } catch (ConflictException | DataIntegrityViolationException e) {
                return false;
            }
        };
    }

    private static String item(Seat seat, String type, String documento) {
        return "{\"seatId\":" + seat.getId() + ",\"ticketType\":\"" + type + "\""
                + (documento == null ? "" : ",\"documentoMeia\":\"" + documento + "\"") + "}";
    }

    private static String sale(Session session, String forma, String email, String... itens) {
        return "{\"sessionId\":" + session.getId()
                + ",\"formaPagamento\":\"" + forma + "\""
                + (email == null ? "" : ",\"clienteEmail\":\"" + email + "\"")
                + ",\"itens\":[" + String.join(",", itens) + "]}";
    }
}
