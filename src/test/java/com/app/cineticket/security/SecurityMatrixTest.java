package com.app.cineticket.security;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

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
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;

    private User owner;
    private String ownerToken;
    private String otherToken;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = userRepository.save(user(OWNER_EMAIL));
        ownerToken = tokenService.generateToken(owner);
        otherToken = tokenService.generateToken(userRepository.save(user(OTHER_EMAIL)));
        ticketId = approvedTicketOf(owner).getId();
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(OWNER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(OTHER_EMAIL).ifPresent(userRepository::delete);
    }

    // --- Validade do token -------------------------------------------------------

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        String expired = new TokenService(SECRET, Duration.ofSeconds(-60)).generateToken(owner);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroSegredoRetorna401() throws Exception {
        String forged = new TokenService("outro_segredo_com_mais_de_32_caracteres!!").generateToken(owner);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoAcessaRotaProtegida() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + ownerToken))
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
            "POST,   /api/v1/rooms",
            "PUT,    /api/v1/rooms/1",
            "DELETE, /api/v1/rooms/1",
            "POST,   /api/v1/sessions",
            "DELETE, /api/v1/sessions/1"
    })
    void escritaNoCatalogoExigeAdmin(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // --- Só o dono acessa o próprio ingresso -------------------------------------

    @Test
    void outroUsuarioNaoVeNaoBaixaENaoCancelaOIngresso() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tickets/" + ticketId + "/pdf").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/tickets/" + ticketId + "/cancel").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void donoBaixaOPdfDoProprioIngresso() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketId + "/pdf").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF));
    }

    private Ticket approvedTicketOf(User user) {
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Matriz");
        cinema.setCnpj("55555555000155");
        cinema.setEndereco("Rua D, 4");
        cinema = cinemaRepository.save(cinema);

        Room room = new Room();
        room.setNome("Sala M");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        Seat seat = new Seat();
        seat.setFila("A");
        seat.setNumero(1);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        Movie movie = new Movie();
        movie.setTitulo("Filme Matriz");
        movie.setDuracaoEmMinutos(110);
        movie = movieRepository.save(movie);

        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        Ticket ticket = new Ticket();
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setStatus(TicketStatus.APPROVED);
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setValorPago(new BigDecimal("30.00"));
        return ticketRepository.save(ticket);
    }

    private static User user(String email) {
        User user = new User();
        user.setNome("Matriz");
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        return user;
    }
}
