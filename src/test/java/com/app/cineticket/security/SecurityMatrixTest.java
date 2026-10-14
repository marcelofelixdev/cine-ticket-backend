package com.app.cineticket.security;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.app.cineticket.support.ApiRequests.authed;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de segurança com a cadeia real de filtros (item 19): validade do JWT,
 * rotas exclusivas de admin e acesso somente do dono aos próprios ingressos.
 */
class SecurityMatrixTest extends AbstractIntegrationTest {

    private static final String SECRET = "test_secret_for_ci_with_at_least_32_chars";
    private static final String OWNER_EMAIL = "matriz-dono@teste.com";
    private static final String OTHER_EMAIL = "matriz-outro@teste.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestData testData;

    private User owner;
    private String ownerToken;
    private String otherToken;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = testData.customer(OWNER_EMAIL);
        ownerToken = testData.token(owner);
        otherToken = testData.token(testData.customer(OTHER_EMAIL));
        ticketId = approvedTicketOf(owner).getId();
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers(OWNER_EMAIL, OTHER_EMAIL);
    }

    // --- Validade do token -------------------------------------------------------

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        String expired = new TokenService(SECRET, Duration.ofSeconds(-60)).generateToken(owner);

        mockMvc.perform(authed(get("/api/v1/users/me"), expired, null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroSegredoRetorna401() throws Exception {
        String forged = new TokenService("outro_segredo_com_mais_de_32_caracteres!!").generateToken(owner);

        mockMvc.perform(authed(get("/api/v1/users/me"), forged, null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoAcessaRotaProtegida() throws Exception {
        mockMvc.perform(authed(get("/api/v1/users/me"), ownerToken, null))
                .andExpect(status().isOk());
    }

    // --- Rotas exclusivas de admin --------------------------------------------------

    @ParameterizedTest(name = "{0} {1} sem ser admin -> 403")
    @CsvSource({
            "POST,   /api/v1/cinemas",
            "PUT,    /api/v1/cinemas/1",
            "DELETE, /api/v1/cinemas/1",
            "POST,   /api/v1/movies",
            "PUT,    /api/v1/movies/1",
            "DELETE, /api/v1/movies/1",
            "POST,   /api/v1/movies/1/tmdb-sync",
            "POST,   /api/v1/rooms",
            "PUT,    /api/v1/rooms/1",
            "DELETE, /api/v1/rooms/1",
            "POST,   /api/v1/sessions",
            "DELETE, /api/v1/sessions/1"
    })
    void escritaNoCatalogoExigeAdmin(String method, String path) throws Exception {
        mockMvc.perform(authed(request(HttpMethod.valueOf(method), path), ownerToken, "{}"))
                .andExpect(status().isForbidden());
    }

    // --- Só o dono acessa o próprio ingresso -------------------------------------

    @Test
    void outroUsuarioNaoVeNaoBaixaENaoCancelaOIngresso() throws Exception {
        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId), otherToken, null))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId + "/pdf"), otherToken, null))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(put("/api/v1/tickets/" + ticketId + "/cancel"), otherToken, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void donoBaixaOPdfDoProprioIngresso() throws Exception {
        mockMvc.perform(authed(get("/api/v1/tickets/" + ticketId + "/pdf"), ownerToken, null))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF));
    }

    private Ticket approvedTicketOf(User user) {
        Room room = testData.room("Sala M", testData.cinema("Cine Matriz", "55555555000155"), 10);
        Session session = testData.session(testData.movie("Filme Matriz", 110), room, LocalDateTime.now().plusDays(1));
        return testData.ticket(session, testData.seat(room, "A", 1), user, TicketStatus.APPROVED);
    }
}
