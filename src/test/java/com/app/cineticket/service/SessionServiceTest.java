package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.mapper.SessionMapper;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private SessionMapper sessionMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SessionService sessionService;

    @Test
    void cancelamentoEmergencialDeveReembolsarSomenteAprovados() {
        var session = new Session();
        session.setId(1L);
        session.setAtivo(true);
        var pending = ticket(10L, TicketStatus.PENDING);
        var approved = ticket(11L, TicketStatus.APPROVED);
        when(sessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
        when(ticketRepository.findBySessionIdForUpdate(1L)).thenReturn(List.of(pending, approved));

        sessionService.deleteEmergency(1L);

        assertEquals(TicketStatus.EMERGENCY_CANCELLED, pending.getStatus());
        assertEquals(TicketStatus.EMERGENCY_CANCELLED, approved.getStatus());
        verify(eventPublisher).publishEvent(new RefundEventDTO(11L, new BigDecimal("40.00")));
    }

    @Test
    void cancelamentoEmergencialDeveSerIdempotente() {
        var session = new Session();
        session.setId(1L);
        session.setAtivo(false);
        when(sessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));

        sessionService.deleteEmergency(1L);

        verify(ticketRepository, never()).findBySessionIdForUpdate(1L);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private Ticket ticket(Long id, TicketStatus status) {
        var ticket = new Ticket();
        ticket.setId(id);
        ticket.setStatus(status);
        ticket.setValorPago(new BigDecimal("40.00"));
        return ticket;
    }
}
