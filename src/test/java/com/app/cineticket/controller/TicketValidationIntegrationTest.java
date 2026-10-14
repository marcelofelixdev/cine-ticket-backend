package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.service.TicketQrCodeService;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Entrada na sala: QR assinado, papel STAFF do próprio cinema, janela de horário e uso único. */
class TicketValidationIntegrationTest extends AbstractIntegrationTest {

    private static final String VALIDATE = "/api/v1/tickets/validate";
    private static final String STAFF_EMAIL = "funcionario@teste.com";
    private static final String CLIENT_EMAIL = "cliente-entrada@teste.com";
    private static final String OTHER_STAFF_EMAIL = "funcionario-outro@teste.com";
    private static final String ADMIN_EMAIL = "admin-entrada@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketQrCodeService qrCodeService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private com.app.cineticket.support.TestData testData;

    private Cinema cinema;
    private Room room;
    private Movie movie;
    private Seat seat;
    private User client;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinema = testData.cinema("Cine Entrada", "66666666000166");
        room = testData.room("Sala E", cinema, 10);
        seat = testData.seat(room, "B", 7);
        movie = testData.movie("Filme da Entrada", 120);

        staffToken = testData.token(STAFF_EMAIL, "ROLE_STAFF", cinema);

        client = testData.customer(CLIENT_EMAIL);
        clientToken = testData.token(client);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(STAFF_EMAIL, CLIENT_EMAIL, OTHER_STAFF_EMAIL, ADMIN_EMAIL);
    }

    @Test
    void funcionarioDeOutroCinemaNaoValida() throws Exception {
        Cinema other = testData.cinema("Cine Vizinho", "77777777000177");
        String otherStaffToken = testData.token(OTHER_STAFF_EMAIL, "ROLE_STAFF", other);

        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));
        validate(qrCodeService.payloadFor(ticket.getId()), otherStaffToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Este ingresso é de outro cinema."));

        assertNull(ticketRepository.findById(ticket.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void adminValidaEmQualquerCinema() throws Exception {
        String adminToken = testData.token(ADMIN_EMAIL, "ROLE_ADMIN", null);

        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));
        validate(qrCodeService.payloadFor(ticket.getId()), adminToken).andExpect(status().isOk());
    }

    @Test
    void funcionarioValidaUmaVezSo() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.MEIA_ESTUDANTE, LocalDateTime.now().plusMinutes(20));
        String qr = qrCodeService.payloadFor(ticket.getId());

        validate(qr, staffToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.cadeiraAssento").value("B-7"))
                .andExpect(jsonPath("$.alerta").value("Meia-entrada (estudante): conferir a carteira de estudante."));

        validate(qr, staffToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Ingresso já utilizado")));

        assertNotNull(ticketRepository.findById(ticket.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void ingressoUsadoNaoPodeSerCancelado() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(50));
        validate(qrCodeService.payloadFor(ticket.getId()), staffToken).andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/v1/tickets/" + ticket.getId() + "/cancel"), clientToken, null))
                .andExpect(status().isConflict());
    }

    @Test
    void qrAdulteradoRetorna400() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));
        String forged = qrCodeService.payloadFor(ticket.getId()).replace("CT1." + ticket.getId(), "CT1." + (ticket.getId() + 1));

        validate(forged, staffToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("QR Code inválido"));
    }

    @Test
    void clienteComumNaoValidaIngresso() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));

        validate(qrCodeService.payloadFor(ticket.getId()), clientToken).andExpect(status().isForbidden());
    }

    @Test
    void foraDoHorarioDaSessaoRetorna422() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusDays(1));

        validate(qrCodeService.payloadFor(ticket.getId()), staffToken)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ingressoCanceladoNaoEntra() throws Exception {
        Ticket ticket = ticket(TicketStatus.CANCELLED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));

        validate(qrCodeService.payloadFor(ticket.getId()), staffToken).andExpect(status().isConflict());
    }

    @Test
    void donoObtemOQrDoIngressoAprovado() throws Exception {
        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusDays(1));

        mockMvc.perform(authed(get("/api/v1/tickets/" + ticket.getId() + "/qr"), clientToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qr").value(qrCodeService.payloadFor(ticket.getId())));

        mockMvc.perform(authed(get("/api/v1/tickets/" + ticket.getId() + "/qr"), staffToken, null))
                .andExpect(status().isForbidden());
    }

    private ResultActions validate(String qr, String token) throws Exception {
        return mockMvc.perform(authed(post(VALIDATE), token, "{\"qr\":\"" + qr + "\"}"));
    }

    private Ticket ticket(TicketStatus status, TicketType type, LocalDateTime sessionStart) {
        Session session = testData.session(movie, room, sessionStart, new BigDecimal("40.00"));
        Ticket ticket = testData.ticket(session, seat, client, status);
        ticket.setTicketType(type);
        return ticketRepository.save(ticket);
    }
}
