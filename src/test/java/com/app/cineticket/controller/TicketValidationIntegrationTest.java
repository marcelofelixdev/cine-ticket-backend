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
import com.app.cineticket.service.TicketQrCodeService;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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
    @Autowired private TokenService tokenService;
    @Autowired private TicketQrCodeService qrCodeService;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

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
        cinema = new Cinema();
        cinema.setNome("Cine Entrada");
        cinema.setCnpj("66666666000166");
        cinema.setEndereco("Rua E, 6");
        cinema = cinemaRepository.save(cinema);

        room = new Room();
        room.setNome("Sala E");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        seat = new Seat();
        seat.setFila("B");
        seat.setNumero(7);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        movie = new Movie();
        movie.setTitulo("Filme da Entrada");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);

        Role staffRole = roleRepository.findByNome("ROLE_STAFF").orElseGet(() -> {
            Role role = new Role();
            role.setNome("ROLE_STAFF");
            return roleRepository.save(role);
        });
        User staff = user("Funcionário", STAFF_EMAIL);
        staff.setRoles(new HashSet<>(Set.of(staffRole)));
        staff.setCinema(cinema);
        staffToken = tokenService.generateToken(userRepository.save(staff));

        client = userRepository.save(user("Cliente", CLIENT_EMAIL));
        clientToken = tokenService.generateToken(client);
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(STAFF_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(CLIENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(OTHER_STAFF_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void funcionarioDeOutroCinemaNaoValida() throws Exception {
        Cinema other = new Cinema();
        other.setNome("Cine Vizinho");
        other.setCnpj("77777777000177");
        other.setEndereco("Rua V, 7");
        other = cinemaRepository.save(other);

        User otherStaff = user("Funcionário vizinho", OTHER_STAFF_EMAIL);
        otherStaff.setRoles(new HashSet<>(Set.of(roleRepository.findByNome("ROLE_STAFF").orElseThrow())));
        otherStaff.setCinema(other);
        String otherStaffToken = tokenService.generateToken(userRepository.save(otherStaff));

        Ticket ticket = ticket(TicketStatus.APPROVED, TicketType.INTEIRA, LocalDateTime.now().plusMinutes(20));
        validate(qrCodeService.payloadFor(ticket.getId()), otherStaffToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Este ingresso é de outro cinema."));

        assertNull(ticketRepository.findById(ticket.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void adminValidaEmQualquerCinema() throws Exception {
        User admin = user("Admin", ADMIN_EMAIL);
        admin.setRoles(new HashSet<>(Set.of(roleRepository.findByNome("ROLE_ADMIN").orElseThrow())));
        String adminToken = tokenService.generateToken(userRepository.save(admin));

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

        mockMvc.perform(put("/api/v1/tickets/" + ticket.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
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

        mockMvc.perform(get("/api/v1/tickets/" + ticket.getId() + "/qr")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qr").value(qrCodeService.payloadFor(ticket.getId())));

        mockMvc.perform(get("/api/v1/tickets/" + ticket.getId() + "/qr")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    private ResultActions validate(String qr, String token) throws Exception {
        return mockMvc.perform(post(VALIDATE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qr + "\"}"));
    }

    private Ticket ticket(TicketStatus status, TicketType type, LocalDateTime sessionStart) {
        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(sessionStart);
        session.setValorBase(new BigDecimal("40.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        Ticket ticket = new Ticket();
        ticket.setUser(client);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setTicketType(type);
        ticket.setValorPago(new BigDecimal("40.00"));
        ticket.setStatus(status);
        return ticketRepository.save(ticket);
    }

    private static User user(String nome, String email) {
        User user = new User();
        user.setNome(nome);
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        return user;
    }
}
