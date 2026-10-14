package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

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
    private static final String EMAIL_DOMAIN = "@concorrencia.teste";

    @Autowired private TicketService ticketService;
    @Autowired private TestData testData;
    @Autowired private TicketRepository ticketRepository;

    private Session session;
    private Seat seat;
    private final List<User> buyers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        Room room = testData.room("Sala Disputada", testData.cinema("Cine Concorrência", "44444444000144"), 10);
        seat = testData.seat(room, "A", 1);
        session = testData.session(testData.movie("Estreia Disputada", 120), room, LocalDateTime.now().plusDays(1));

        buyers.clear();
        for (int i = 0; i < BUYERS; i++) {
            buyers.add(testData.customer("comprador-" + i + EMAIL_DOMAIN));
        }
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsersEndingWith(EMAIL_DOMAIN);
    }

    @Test
    void comprasSimultaneasDoMesmoAssentoVendemExatamenteUmIngresso() throws Exception {
        TicketRequestDTO request = new TicketRequestDTO(
                session.getId(), seat.getId(), TicketType.INTEIRA, null);
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
            try {
                ready.countDown();
                start.await();
                ticketService.buyTicket(request, buyer);
                return Outcome.SUCCESS;
            } catch (ConflictException | DataIntegrityViolationException e) {
                // ConflictException: barrado pela verificação após o lock.
                // DataIntegrityViolation: barrado pelo índice (as duas viram 409 na API).
                return Outcome.CONFLICT;
            }
        };
    }

    private Ticket ticket(User user, TicketStatus status) {
        return testData.ticket(session, seat, user, status);
    }
}
