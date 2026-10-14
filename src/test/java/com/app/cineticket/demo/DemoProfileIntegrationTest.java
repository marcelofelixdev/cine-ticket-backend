package com.app.cineticket.demo;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil demo de ponta a ponta. Usa um contexto (e containers) próprio por ativar o perfil
 * demo. O TMDB aponta para uma porta fechada para exercitar o plano B do catálogo.
 */
@ActiveProfiles({"dev", "demo"})
@TestPropertySource(properties = "tmdb.api.url=http://localhost:1")
class DemoProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DemoDataService demoDataService;
    @Autowired private MovieRepository movieRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.app.cineticket.support.TestData testData;

    @Test
    void bootPopulaCatalogoCinemasSalasEVisitante() {
        assertEquals(DemoCatalog.MOVIES.size(), movieRepository.count());
        assertEquals(2, cinemaRepository.count());
        assertEquals(4, roomRepository.count());
        assertEquals(120 + 80 + 100 + 40, seatRepository.count());
        assertTrue(userRepository.existsByEmailIgnoreCase(demoDataService.getVisitorEmail()));
        // TMDB fora do ar neste teste: direção e gêneros vêm do plano B do catálogo local.
        assertTrue(movieRepository.findAll().stream().allMatch(movie -> movie.getDiretor() != null && movie.getGeneros() != null));
    }

    @Test
    void todosOsFilmesEstaoEmCartazNoCatalogoPublico() throws Exception {
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(DemoCatalog.MOVIES.size()));
    }

    @Test
    void seedRepetidoNaoDuplicaNada() {
        long sessions = sessionRepository.count();

        demoDataService.seed();

        assertEquals(DemoCatalog.MOVIES.size(), movieRepository.count());
        assertEquals(2, cinemaRepository.count());
        assertEquals(sessions, sessionRepository.count());
    }

    @Test
    @Transactional
    void programacaoTemSessoesFuturasSemSobreposicaoNasSalas() {
        List<Session> sessions = sessionRepository.findAll();
        assertFalse(sessions.isEmpty());

        LocalDateTime limit = LocalDateTime.now().plusDays(DemoCatalog.DAYS_AHEAD + 1L);
        Map<Long, List<Session>> byRoom = sessions.stream()
                .collect(Collectors.groupingBy(session -> session.getRoom().getId()));
        for (List<Session> roomSessions : byRoom.values()) {
            roomSessions.sort(Comparator.comparing(Session::getHorarioInicio));
            for (int i = 0; i < roomSessions.size(); i++) {
                Session current = roomSessions.get(i);
                assertTrue(current.getHorarioInicio().isBefore(limit));
                if (i > 0) {
                    Session previous = roomSessions.get(i - 1);
                    LocalDateTime previousEnd = previous.getHorarioInicio()
                            .plusMinutes(previous.getMovie().getDuracaoEmMinutos() + 30L);
                    assertFalse(current.getHorarioInicio().isBefore(previousEnd), "sessões sobrepostas na sala");
                }
            }
        }
    }

    @Test
    void resetApagaComprasEContasDeVisitantesERecriaAProgramacao() {
        User extraAccount = new User();
        extraAccount.setNome("Conta criada no demo");
        extraAccount.setEmail("criado-no-demo@teste.com");
        extraAccount.setSenha("hash-irrelevante");
        extraAccount = userRepository.save(extraAccount);
        ticketRepository.save(ticketFor(extraAccount));

        demoDataService.reset();

        assertEquals(0, ticketRepository.count());
        assertFalse(userRepository.existsByEmailIgnoreCase("criado-no-demo@teste.com"));
        assertTrue(userRepository.existsByEmailIgnoreCase(demoDataService.getVisitorEmail()));
        assertEquals(DemoCatalog.MOVIES.size(), movieRepository.count());
        assertTrue(sessionRepository.count() > 0);
    }

    @Test
    void equipeSemCinemaEVinculadaAoPrimeiroCinemaEAEquipeDeExemploSobreviveAoReset() {
        User exampleManager = testData.user("gerente@cineticket.dev", "ROLE_MANAGER", null);
        User createdByVisitor = testData.user("funcionario-criado-no-demo@teste.com", "ROLE_STAFF", null);

        demoDataService.seed();

        Long firstCinemaId = cinemaRepository.findFirstByOrderByIdAsc().orElseThrow().getId();
        assertEquals(firstCinemaId, userRepository.findById(exampleManager.getId()).orElseThrow().getCinema().getId());
        assertEquals(firstCinemaId, userRepository.findById(createdByVisitor.getId()).orElseThrow().getCinema().getId());

        demoDataService.reset();

        assertTrue(userRepository.existsByEmailIgnoreCase("gerente@cineticket.dev"));
        assertFalse(userRepository.existsByEmailIgnoreCase("funcionario-criado-no-demo@teste.com"));
        userRepository.deleteById(exampleManager.getId());
    }

    @Test
    void infoEPublicaEOVisitanteConsegueEntrar() throws Exception {
        mockMvc.perform(get("/api/v1/demo/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.visitanteEmail").value(demoDataService.getVisitorEmail()))
                .andExpect(jsonPath("$.gerenteEmail").value("gerente@cineticket.dev"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("10.9.0.1"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + demoDataService.getVisitorEmail()
                                + "\",\"senha\":\"" + demoDataService.getVisitorPassword() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void visitanteNaoEBloqueadoPorSenhasErradas() throws Exception {
        for (int i = 0; i < 6; i++) {
            final int n = i;
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> { request.setRemoteAddr("10.9.1." + (n + 1)); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + demoDataService.getVisitorEmail()
                                    + "\",\"senha\":\"senha-errada-123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        User visitor = userRepository.findByEmailIgnoreCase(demoDataService.getVisitorEmail()).orElseThrow();
        assertEquals(null, visitor.getLockedUntil());
    }

    private Ticket ticketFor(User user) {
        Session session = sessionRepository.findAll().get(0);
        Seat seat = seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(session.getRoom().getId()).get(0);
        Ticket ticket = new Ticket();
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setValorPago(new BigDecimal("30.00"));
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return ticket;
    }
}
