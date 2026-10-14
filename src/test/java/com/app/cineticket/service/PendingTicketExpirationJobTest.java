package com.app.cineticket.service;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestData;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Roda a query real de expiração (JPQL em lote) contra o banco H2.
 */
class PendingTicketExpirationJobTest extends AbstractIntegrationTest {

    @Autowired private TestData testData;
    @Autowired private PendingTicketExpirationJob job;
    @Autowired private TicketRepository ticketRepository;

    private Session session;
    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        cleanUp();
        room = testData.room("Sala 1", testData.cinema("Cine Teste", "12345678000199"), 10);
        session = testData.session(testData.movie("Filme Teste", 120), room, LocalDateTime.now().plusDays(1));
        user = testData.customer("cliente-expiracao@teste.com");
    }

    @AfterEach
    void cleanUp() {
        testData.deleteCatalog();
        testData.deleteUsers("cliente-expiracao@teste.com");
    }

    @Test
    void expiraSomenteIngressosPendentesComPrazoVencido() {
        LocalDateTime agora = LocalDateTime.now();
        Long vencido = ticket(1, TicketStatus.PENDING, agora.minusMinutes(1));
        Long noPrazo = ticket(2, TicketStatus.PENDING, agora.plusMinutes(5));
        Long aprovado = ticket(3, TicketStatus.APPROVED, agora.minusMinutes(1));

        job.expirePendingTickets();

        assertEquals(TicketStatus.EXPIRED, statusOf(vencido));
        assertEquals(TicketStatus.PENDING, statusOf(noPrazo));
        assertEquals(TicketStatus.APPROVED, statusOf(aprovado));
    }

    private Long ticket(int seatNumber, TicketStatus status, LocalDateTime expiresAt) {
        Ticket ticket = testData.ticket(session, testData.seat(room, "A", seatNumber), user, status);
        ticket.setExpiresAt(expiresAt);
        return ticketRepository.save(ticket).getId();
    }

    private TicketStatus statusOf(Long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getStatus();
    }
}
