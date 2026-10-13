package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.exception.ConflictException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova de que não existe venda dupla do mesmo assento, contra o PostgreSQL real.
 * <p>
 * Duas camadas de proteção:
 * <ol>
 *     <li>Lock pessimista no assento ({@code SELECT ... FOR UPDATE}) dentro da compra,
 *     que serializa compradores simultâneos;</li>
 *     <li>Índice único parcial {@code idx_ticket_no_overbooking (session_id, seat_id)
 *     WHERE status IN ('APPROVED','PENDING')}, que barra no banco mesmo se o código falhar.</li>
 * </ol>
 */
class OverbookingConcurrencyTest extends AbstractIntegrationTest {

    /** Abaixo do pool de conexões do perfil dev (10), para não medir falta de conexão. */
    private static final int BUYERS = 8;
    private static final String EMAIL_PREFIX = "comprador-concorrente-";

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;

    private Session session;
    private Seat seat;
    private final List<User> buyers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Concorrência");
        cinema.setCnpj("44444444000144");
        cinema.setEndereco("Rua C, 3");
        cinema = cinemaRepository.save(cinema);

        Room room = new Room();
        room.setNome("Sala Disputada");
        room.setCapacidade(10);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        seat = new Seat();
        seat.setFila("A");
        seat.setNumero(1);
        seat.setRoom(room);
        seat = seatRepository.save(seat);

        Movie movie = new Movie();
        movie.setTitulo("Estreia Disputada");
        movie.setDuracaoEmMinutos(120);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        buyers.clear();
        for (int i = 0; i < BUYERS; i++) {
            User user = new User();
            user.setNome("Comprador " + i);
            user.setEmail(EMAIL_PREFIX + i + "@teste.com");
            user.setSenha("hash-irrelevante");
            buyers.add(userRepository.save(user));
        }
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        for (int i = 0; i < BUYERS; i++) {
            userRepository.findByEmailIgnoreCase(EMAIL_PREFIX + i + "@teste.com").ifPresent(userRepository::delete);
        }
    }

    @Test
    void comprasSimultaneasDoMesmoAssentoVendemExatamenteUmIngresso() throws Exception {
        TicketRequestDTO request = new TicketRequestDTO(
                session.getId(), seat.getId(), TicketType.INTEIRA, "tok_test");
        CountDownLatch ready = new CountDownLatch(BUYERS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);

        List<Future<Outcome>> futures = new ArrayList<>();
        for (User buyer : buyers) {
            futures.add(pool.submit(purchase(buyer, request, ready, start)));
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown(); // todos os compradores disparam juntos
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "compras não terminaram a tempo");

        int successes = 0;
        int conflicts = 0;
        for (Future<Outcome> future : futures) {
            switch (future.get()) {
                case SUCCESS -> successes++;
                case CONFLICT -> conflicts++;
            }
        }

        assertEquals(1, successes, "exatamente um comprador deve conseguir o assento");
        assertEquals(BUYERS - 1, conflicts, "os demais devem receber conflito (409)");
        long activeTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == TicketStatus.PENDING || t.getStatus() == TicketStatus.APPROVED)
                .count();
        assertEquals(1, activeTickets, "o banco deve ter um único ingresso ativo para o assento");
    }

    @Test
    void indiceParcialBarraNoBancoUmSegundoIngressoAtivoParaOMesmoAssento() {
        ticketRepository.saveAndFlush(ticket(buyers.get(0), TicketStatus.PENDING));

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(ticket(buyers.get(1), TicketStatus.APPROVED)));
    }

    @Test
    void indiceParcialPermiteRevenderAssentoDeIngressoCanceladoOuExpirado() {
        ticketRepository.saveAndFlush(ticket(buyers.get(0), TicketStatus.CANCELLED));
        ticketRepository.saveAndFlush(ticket(buyers.get(1), TicketStatus.EXPIRED));

        ticketRepository.saveAndFlush(ticket(buyers.get(2), TicketStatus.PENDING));

        assertEquals(3, ticketRepository.count());
    }

    private enum Outcome { SUCCESS, CONFLICT }

    private Callable<Outcome> purchase(User buyer, TicketRequestDTO request,
                                       CountDownLatch ready, CountDownLatch start) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(buyer, null, List.of()));
            try {
                ready.countDown();
                start.await();
                ticketService.buyTicket(request);
                return Outcome.SUCCESS;
            } catch (ConflictException | DataIntegrityViolationException e) {
                // ConflictException: barrado pela verificação após o lock.
                // DataIntegrityViolation: barrado pelo índice (as duas viram 409 na API).
                return Outcome.CONFLICT;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private Ticket ticket(User user, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setStatus(status);
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setValorPago(new BigDecimal("30.00"));
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return ticket;
    }
}
