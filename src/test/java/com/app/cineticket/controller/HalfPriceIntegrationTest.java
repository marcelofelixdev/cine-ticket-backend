package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Meia-entrada ponta a ponta: perfil com data de nascimento, carteira de estudante e cota de 40%. */
class HalfPriceIntegrationTest extends AbstractIntegrationTest {

    /** Um e-mail por teste: o rate limit de compras (5/min) é por usuário. */
    private String email;

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private Session session;
    private List<Seat> seats;
    private String token;

    @BeforeEach
    void setUp() {
        cleanUp();
        email = "meia-" + System.nanoTime() + "@teste.com";
        Cinema cinema = testData.cinema("Cine Meia", "77777777000177");
        Room room = testData.room("Sala M", cinema, 5); // cota de meia: 2 lugares
        seats = testData.seats(room, "A", 5);
        Movie movie = testData.movie("Filme da Meia", 100);
        session = testData.session(movie, room, LocalDateTime.now().plusDays(1), new BigDecimal("40.00"));
        token = testData.token(testData.customer(email));
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        if (email != null) {
            testData.deleteUsers(email);
        }
    }

    @Test
    void idosoInformaANascimentoNoPerfilEPagaMeia() throws Exception {
        buy(0, "MEIA_IDOSO", null).andExpect(status().isUnprocessableEntity());

        mockMvc.perform(authed(patch("/api/v1/users/me"), token, "{\"dataNascimento\":\"1950-03-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1950-03-10"));

        buy(0, "MEIA_IDOSO", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorPago").value(20.0));
    }

    @Test
    void cotaDeMeiaEsgotaMasAInteiraContinua() throws Exception {
        buy(0, "MEIA_ESTUDANTE", "CIE-0001").andExpect(status().isCreated());
        buy(1, "MEIA_ESTUDANTE", "CIE-0002").andExpect(status().isCreated());

        buy(2, "MEIA_ESTUDANTE", "CIE-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Cota de meia-entrada esgotada")));

        buy(2, "INTEIRA", null).andExpect(status().isCreated());
    }

    private ResultActions buy(int seatIndex, String type, String documento) throws Exception {
        String doc = documento == null ? "" : ",\"documentoMeia\":\"" + documento + "\"";
        return mockMvc.perform(authed(post("/api/v1/tickets"), token, "{\"sessionId\":" + session.getId() + ",\"seatId\":" + seats.get(seatIndex).getId()
                        + ",\"ticketType\":\"" + type + "\"" + doc + "}"));
    }
}
