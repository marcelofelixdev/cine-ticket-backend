package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lugares livres, meias restantes e receita por sessão. Sala de 10 lugares (cota de 4 meias)
 * com uma inteira paga (R$ 30), uma reserva pendente, uma meia paga no balcão (R$ 15) e um
 * ingresso cancelado, que não conta.
 */
class SessionAvailabilityIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@disponibilidade.teste";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private Session session;
    private String managerToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = testData.cinema("Cine Disponibilidade", "33444555000166");
        Room room = testData.room("Sala D", cinema, 10);
        List<Seat> seats = testData.seats(room, "A", 10);
        session = testData.session(testData.movie("Filme Disponível", 100), room,
                LocalDateTime.now().plusDays(1).withHour(20).withMinute(0));

        User customer = testData.customer("cliente" + DOMAIN);
        User staff = testData.user("caixa" + DOMAIN, "ROLE_STAFF", cinema);
        testData.ticket(session, seats.get(0), customer, TicketStatus.APPROVED);
        testData.ticket(session, seats.get(1), customer, TicketStatus.PENDING);
        testData.counterTicket(session, seats.get(2), TicketType.MEIA_ESTUDANTE, FormaPagamento.DINHEIRO, staff, null);
        testData.ticket(session, seats.get(3), customer, TicketStatus.CANCELLED);

        managerToken = testData.token("gerente" + DOMAIN, "ROLE_MANAGER", cinema);
        staffToken = testData.token(staff);
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(DOMAIN);
    }

    @Test
    void sessaoPublicaMostraLugaresLivresEMeiasRestantes() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/" + session.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugaresLivres").value(7))
                .andExpect(jsonPath("$.meiasRestantes").value(3));

        mockMvc.perform(get("/api/v1/sessions").param("movieId", session.getMovie().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lugaresLivres").value(7))
                .andExpect(jsonPath("$.content[0].meiasRestantes").value(3));

        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assentosLivres").value(7))
                .andExpect(jsonPath("$.meiasRestantes").value(3));
    }

    @Test
    void gerenteVeMeiasEReceitaECaixaNaoVeReceita() throws Exception {
        String day = session.getHorarioInicio().toLocalDate().toString();

        mockMvc.perform(authed(get("/api/v1/manager/sessions").param("date", day), managerToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ocupados").value(3))
                .andExpect(jsonPath("$[0].meias").value(1))
                .andExpect(jsonPath("$[0].receita").value(45.0));

        mockMvc.perform(authed(get("/api/v1/box-office/sessions").param("date", day), staffToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ocupados").value(3))
                .andExpect(jsonPath("$[0].meias").value(1))
                .andExpect(jsonPath("$[0].receita").value(nullValue()));
    }
}
