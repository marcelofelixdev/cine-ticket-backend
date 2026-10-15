package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Painel de ingressos: lista com filtros, resumo da sessão e cancelamento de venda de balcão. */
class TicketAdministrationIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@ingressos.teste";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;
    @Autowired private TicketRepository ticketRepository;

    private Session sessionA;
    private Session sessionB;
    private Ticket online;
    private Ticket pending;
    private Ticket counterHalf;
    private Ticket counterUsed;
    private Ticket otherCinema;
    private String managerToken;
    private String adminToken;
    private String staffToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinemaA = testData.cinema("Cine Painel A", "52525252000152");
        Cinema cinemaB = testData.cinema("Cine Painel B", "53535353000153");
        Room roomA = testData.room("Sala Painel A", cinemaA, 10);
        Room roomB = testData.room("Sala Painel B", cinemaB, 10);
        List<Seat> seats = testData.seats(roomA, "A", 4);
        Movie movie = testData.movie("Filme do Painel", 120);
        sessionA = testData.session(movie, roomA, LocalDateTime.now().plusDays(1));
        sessionB = testData.session(movie, roomB, LocalDateTime.now().plusDays(1));

        User manager = testData.user("gerente" + DOMAIN, "ROLE_MANAGER", cinemaA);
        managerToken = testData.token(manager);
        adminToken = testData.token("admin" + DOMAIN, "ROLE_ADMIN", null);
        staffToken = testData.token("caixa" + DOMAIN, "ROLE_STAFF", cinemaA);
        User client = testData.customer("cliente" + DOMAIN);
        clientToken = testData.token(client);

        online = testData.ticket(sessionA, seats.get(0), client, TicketStatus.APPROVED);
        pending = testData.ticket(sessionA, seats.get(1), client, TicketStatus.PENDING);
        counterHalf = testData.counterTicket(sessionA, seats.get(2), TicketType.MEIA_ESTUDANTE, FormaPagamento.DINHEIRO,
                manager, "balcao-meia@cliente.teste");
        counterUsed = testData.counterTicket(sessionA, seats.get(3), TicketType.INTEIRA, FormaPagamento.PIX, manager, null);
        counterUsed.setUsedAt(LocalDateTime.now());
        counterUsed = ticketRepository.save(counterUsed);
        otherCinema = testData.ticket(sessionB, testData.seat(roomB, "A", 1), client, TicketStatus.APPROVED);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
    }

    @Test
    void gerenteListaSoOProprioCinemaComFiltros() throws Exception {
        mockMvc.perform(authed(get("/api/v1/manager/tickets"), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));
        mockMvc.perform(authed(get("/api/v1/manager/tickets").param("canal", "BALCAO"), managerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(authed(get("/api/v1/manager/tickets").param("status", "PENDING"), managerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending.getId()));
        mockMvc.perform(authed(get("/api/v1/manager/tickets").param("email", "BALCAO-MEIA"), managerToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].vendidoPor").value("gerente" + DOMAIN))
                .andExpect(jsonPath("$.content[0].assento").value("A-3"));
    }

    @Test
    void adminListaARedeEFiltraPorCinema() throws Exception {
        mockMvc.perform(authed(get("/api/v1/admin/tickets"), adminToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(5));
        mockMvc.perform(authed(get("/api/v1/admin/tickets")
                        .param("cinemaId", String.valueOf(sessionB.getRoom().getCinema().getId())), adminToken, null))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(otherCinema.getId()));
    }

    @Test
    void resumoDaSessaoComOcupacaoMeiasEReceita() throws Exception {
        mockMvc.perform(authed(get("/api/v1/manager/sessions/" + sessionA.getId() + "/summary"), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares").value(10))
                .andExpect(jsonPath("$.vendidos").value(3))
                .andExpect(jsonPath("$.reservados").value(1))
                .andExpect(jsonPath("$.livres").value(6))
                .andExpect(jsonPath("$.meias").value(1))
                .andExpect(jsonPath("$.entradas").value(1))
                .andExpect(jsonPath("$.receitaTotal").value(75.00))
                .andExpect(jsonPath("$.receitaOnline").value(30.00))
                .andExpect(jsonPath("$.receitaBalcao").value(45.00))
                .andExpect(jsonPath("$.receitaBalcaoPorForma.DINHEIRO").value(15.00))
                .andExpect(jsonPath("$.receitaBalcaoPorForma.PIX").value(30.00));

        mockMvc.perform(authed(get("/api/v1/manager/sessions/" + sessionB.getId() + "/summary"), managerToken, null))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/admin/sessions/" + sessionB.getId() + "/summary"), adminToken, null))
                .andExpect(status().isOk());
    }

    @Test
    void gerenteCancelaVendaDeBalcaoEDevolveNoCaixa() throws Exception {
        String cancel = "/api/v1/manager/tickets/" + counterHalf.getId() + "/cancel";
        mockMvc.perform(authed(post(cancel), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reembolso").value("REFUNDED"));
        mockMvc.perform(authed(post(cancel), managerToken, null)).andExpect(status().isConflict());
    }

    @Test
    void cancelamentoNoCaixaSoParaVendaDeBalcaoDoProprioCinemaENaoUsada() throws Exception {
        mockMvc.perform(authed(post("/api/v1/manager/tickets/" + online.getId() + "/cancel"), managerToken, null))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(authed(post("/api/v1/manager/tickets/" + counterUsed.getId() + "/cancel"), managerToken, null))
                .andExpect(status().isConflict());
        mockMvc.perform(authed(post("/api/v1/manager/tickets/" + otherCinema.getId() + "/cancel"), managerToken, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelamentoEmergencialDeixaADevolucaoDoBalcaoPendente() throws Exception {
        mockMvc.perform(authed(delete("/api/v1/manager/sessions/" + sessionA.getId()), managerToken, null))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/admin/tickets")
                        .param("sessionId", String.valueOf(sessionA.getId()))
                        .param("canal", "BALCAO")
                        .param("email", "balcao-meia"), adminToken, null))
                .andExpect(jsonPath("$.content[0].status").value("EMERGENCY_CANCELLED"))
                .andExpect(jsonPath("$.content[0].reembolso").value("PENDING"));
    }

    @Test
    void caixaDevolveOValorDaSessaoCanceladaUmaVezSo() throws Exception {
        mockMvc.perform(authed(delete("/api/v1/manager/sessions/" + sessionA.getId()), managerToken, null))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/box-office/refunds/pending"), staffToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        String confirm = "/api/v1/box-office/refunds/" + counterHalf.getId() + "/confirm";
        mockMvc.perform(authed(post(confirm), staffToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.valor").value(15.00))
                .andExpect(jsonPath("$.devolvidoEm").exists());
        mockMvc.perform(authed(post(confirm), staffToken, null)).andExpect(status().isNotFound());

        mockMvc.perform(authed(get("/api/v1/box-office/refunds/pending"), staffToken, null))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void devolucaoSoNoCaixaDoProprioCinema() throws Exception {
        mockMvc.perform(authed(delete("/api/v1/manager/sessions/" + sessionA.getId()), managerToken, null))
                .andExpect(status().isNoContent());
        String otherStaff = testData.token("caixa-b" + DOMAIN, "ROLE_STAFF", sessionB.getRoom().getCinema());
        String confirm = "/api/v1/box-office/refunds/" + counterHalf.getId() + "/confirm";

        mockMvc.perform(authed(post(confirm), otherStaff, null)).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/box-office/refunds/pending"), otherStaff, null))
                .andExpect(jsonPath("$.length()").value(0));
        for (String token : List.of(clientToken, adminToken)) {
            mockMvc.perform(authed(post(confirm), token, null)).andExpect(status().isForbidden());
        }
    }

    @Test
    void soGerenteEAdminVeemOPainelDeIngressos() throws Exception {
        for (String token : List.of(staffToken, clientToken)) {
            mockMvc.perform(authed(get("/api/v1/manager/tickets"), token, null)).andExpect(status().isForbidden());
            mockMvc.perform(authed(post("/api/v1/manager/tickets/" + counterHalf.getId() + "/cancel"), token, null))
                    .andExpect(status().isForbidden());
        }
        // O admin usa as rotas do cinema informando qual; o ingresso tem de ser dele.
        String cinemaB = String.valueOf(sessionB.getRoom().getCinema().getId());
        mockMvc.perform(authed(get("/api/v1/manager/tickets"), adminToken, null)).andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/v1/manager/tickets/" + counterHalf.getId() + "/cancel").param("cinemaId", cinemaB),
                        adminToken, null))
                .andExpect(status().isForbidden());
        for (String token : List.of(staffToken, clientToken, managerToken)) {
            mockMvc.perform(authed(get("/api/v1/admin/tickets"), token, null)).andExpect(status().isForbidden());
        }
    }
}
