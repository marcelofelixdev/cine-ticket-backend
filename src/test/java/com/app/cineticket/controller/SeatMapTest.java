package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mapa de assentos ponta a ponta: endpoint público, com H2 e os filtros de segurança ligados.
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
class SeatMapTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomService roomService;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private RabbitTemplate rabbitTemplate;

    private Session session;
    private List<Seat> seats;
    private User user;

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Teste");
        cinema.setCnpj("12345678000199");
        cinema.setEndereco("Rua A, 1");
        Long cinemaId = cinemaRepository.save(cinema).getId();

        Long roomId = roomService.create(new RoomRequestDTO("Sala 1", 12, cinemaId)).id();
        seats = seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(roomId);

        Movie movie = new Movie();
        movie.setTitulo("Filme Teste");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        session.setMovie(movie);
        session.setRoom(roomRepository.findById(roomId).orElseThrow());
        session = sessionRepository.save(session);

        user = new User();
        user.setNome("Cliente");
        user.setEmail("cliente-mapa@teste.com");
        user.setSenha("hash-irrelevante");
        user = userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.findByEmailIgnoreCase("cliente-mapa@teste.com").ifPresent(userRepository::delete);
    }

    @Test
    void mostraAssentosLivresEOcupadosSemPrecisarDeLogin() throws Exception {
        ticket(seats.get(0), TicketStatus.APPROVED);
        ticket(seats.get(1), TicketStatus.PENDING);
        ticket(seats.get(2), TicketStatus.CANCELLED);
        ticket(seats.get(3), TicketStatus.EXPIRED);

        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.salaNome").value("Sala 1"))
                .andExpect(jsonPath("$.totalAssentos").value(12))
                .andExpect(jsonPath("$.assentosLivres").value(10))
                .andExpect(jsonPath("$.assentos[0].fila").value("A"))
                .andExpect(jsonPath("$.assentos[0].numero").value(1))
                .andExpect(jsonPath("$.assentos[0].status").value("OCUPADO"))
                .andExpect(jsonPath("$.assentos[1].status").value("OCUPADO"))
                .andExpect(jsonPath("$.assentos[2].status").value("LIVRE"))
                .andExpect(jsonPath("$.assentos[3].status").value("LIVRE"))
                .andExpect(jsonPath("$.assentos[10].fila").value("B"));
    }

    @Test
    void sessaoInativaRetorna404() throws Exception {
        session.setAtivo(false);
        sessionRepository.save(session);

        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/seats"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessaoInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/999999/seats"))
                .andExpect(status().isNotFound());
    }

    private void ticket(Seat seat, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setStatus(status);
        ticket.setValorPago(new BigDecimal("30.00"));
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setUser(user);
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        ticketRepository.save(ticket);
    }
}
