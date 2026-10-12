package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultas do item 9: filtros de sessões e os endpoints de detalhe
 * (sessão, sala, ingresso e usuário logado).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "api.security.token.secret=test_secret_for_ci_with_at_least_32_chars",
        "TMDB_API_KEY=test_tmdb_key",
        "app.bootstrap.admin.enabled=false"
})
@AutoConfigureMockMvc
class CatalogQueriesTest {

    private static final String SESSIONS = "/api/v1/sessions";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private User owner;
    private String ownerToken;
    private String otherToken;
    private Movie movieA;
    private Cinema cinema1;
    private Cinema cinema2;
    private Room room1;
    private Session sessionAmanhaFilmeA;
    private Session sessionInativa;

    @BeforeEach
    void setUp() {
        cleanUp();
        Role userRole = roleRepository.save(role("ROLE_USER"));
        owner = userRepository.save(user("dono@teste.com", userRole));
        ownerToken = tokenService.generateToken(owner);
        otherToken = tokenService.generateToken(userRepository.save(user("outro@teste.com", userRole)));

        cinema1 = cinema("Cine Centro", "11111111000111");
        cinema2 = cinema("Cine Shopping", "22222222000122");
        room1 = room("Sala 1", cinema1);
        Room room2 = room("Sala 2", cinema2);
        movieA = movie("Filme A");
        Movie movieB = movie("Filme B");

        LocalDate amanha = LocalDate.now().plusDays(1);
        session(movieA, room1, LocalDateTime.now().minusDays(1), true);
        sessionAmanhaFilmeA = session(movieA, room1, amanha.atTime(15, 0), true);
        session(movieB, room2, amanha.plusDays(1).atTime(15, 0), true);
        sessionInativa = session(movieB, room2, amanha.atTime(20, 0), false);
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void listaPorPadraoSoSessoesAtivasEFuturas() throws Exception {
        mockMvc.perform(auth(get(SESSIONS), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void incluiSessoesPassadasQuandoPedido() throws Exception {
        mockMvc.perform(auth(get(SESSIONS).param("includePast", "true"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtraPorFilme() throws Exception {
        mockMvc.perform(auth(get(SESSIONS).param("movieId", movieA.getId().toString()), ownerToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movieTitulo").value("Filme A"));
    }

    @Test
    void filtraPorCinema() throws Exception {
        mockMvc.perform(auth(get(SESSIONS).param("cinemaId", cinema2.getId().toString()), ownerToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cinemaNome").value("Cine Shopping"));
    }

    @Test
    void filtraPorData() throws Exception {
        mockMvc.perform(auth(get(SESSIONS).param("date", LocalDate.now().plusDays(1).toString()), ownerToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sessionAmanhaFilmeA.getId()));
    }

    @Test
    void detalheDaSessaoAtivaENaoMostraSessaoCancelada() throws Exception {
        mockMvc.perform(auth(get(SESSIONS + "/" + sessionAmanhaFilmeA.getId()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitulo").value("Filme A"))
                .andExpect(jsonPath("$.cinemaNome").value("Cine Centro"));

        mockMvc.perform(auth(get(SESSIONS + "/" + sessionInativa.getId()), ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalheDaSala() throws Exception {
        mockMvc.perform(auth(get("/api/v1/rooms/" + room1.getId()), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sala 1"))
                .andExpect(jsonPath("$.cinemaNome").value("Cine Centro"));
    }

    @Test
    void ingressoSoPodeSerVistoPeloDono() throws Exception {
        Long ticketId = ticket().getId();

        mockMvc.perform(auth(get("/api/v1/tickets/" + ticketId), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(auth(get("/api/v1/tickets/" + ticketId), otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void retornaOUsuarioLogado() throws Exception {
        mockMvc.perform(auth(get("/api/v1/users/me"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dono@teste.com"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private Ticket ticket() {
        Seat seat = new Seat();
        seat.setFila("A");
        seat.setNumero(1);
        seat.setRoom(room1);
        seat = seatRepository.save(seat);

        Ticket ticket = new Ticket();
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setValorPago(new BigDecimal("30.00"));
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setSession(sessionAmanhaFilmeA);
        ticket.setSeat(seat);
        ticket.setUser(owner);
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return ticketRepository.save(ticket);
    }

    private Cinema cinema(String nome, String cnpj) {
        Cinema cinema = new Cinema();
        cinema.setNome(nome);
        cinema.setCnpj(cnpj);
        cinema.setEndereco("Rua A, 1");
        return cinemaRepository.save(cinema);
    }

    private Room room(String nome, Cinema cinema) {
        Room room = new Room();
        room.setNome(nome);
        room.setCapacidade(10);
        room.setCinema(cinema);
        return roomRepository.save(room);
    }

    private Movie movie(String titulo) {
        Movie movie = new Movie();
        movie.setTitulo(titulo);
        movie.setDuracaoEmMinutos(120);
        return movieRepository.save(movie);
    }

    private Session session(Movie movie, Room room, LocalDateTime inicio, boolean ativa) {
        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(inicio);
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(ativa);
        return sessionRepository.save(session);
    }

    private static Role role(String nome) {
        Role role = new Role();
        role.setNome(nome);
        return role;
    }

    private static User user(String email, Role... roles) {
        User user = new User();
        user.setNome("Teste");
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }
}
