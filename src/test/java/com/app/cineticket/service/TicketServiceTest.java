package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.PaymentProducer;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PdfService pdfService;

    @InjectMocks
    private TicketService ticketService;

    @Test
    @DisplayName("Deve lançar BusinessException ao tentar comprar uma cadeira já vendida")
    void deveLancarErroQuandoCadeiraJaVendida() {
        TicketRequestDTO request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, "tok_fake_123");

        com.app.cineticket.domain.entity.Room room = new com.app.cineticket.domain.entity.Room();
        room.setId(10L);

        com.app.cineticket.domain.entity.Seat seat = new com.app.cineticket.domain.entity.Seat();
        seat.setId(2L);
        seat.setRoom(room);

        com.app.cineticket.domain.entity.Session session = new com.app.cineticket.domain.entity.Session();
        session.setId(1L);
        session.setRoom(room);
        session.setAtivo(true);
        session.setHorarioInicio(java.time.LocalDateTime.now().plusHours(2));

        when(seatRepository.findByIdWithLock(2L)).thenReturn(Optional.of(seat));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        when(ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(
                eq(1L), eq(2L), anyList()
        )).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            ticketService.buyTicket(request);
        });

        assertEquals("OVERBOOKING: Esta cadeira já está ocupada para esta sessão.", exception.getMessage());

        Mockito.verify(ticketRepository, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Deve impedir cancelamento faltando menos de 30 minutos para o filme")
    void deveImpedirCancelamentoEmCimaDaHora() {
        Session sessaoEmCimaDaHora = new Session();

        sessaoEmCimaDaHora.setHorarioInicio(java.time.LocalDateTime.now().plusMinutes(10));

        com.app.cineticket.domain.entity.User usuarioLogado = new com.app.cineticket.domain.entity.User();
        usuarioLogado.setId(100L);

        Ticket ticketFantasma = new Ticket();
        ticketFantasma.setUser(usuarioLogado);
        ticketFantasma.setSession(sessaoEmCimaDaHora);
        ticketFantasma.setStatus(TicketStatus.APPROVED);

        // Authentication authentication = Mockito.mock(Authentication.class);
        // SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        // when(securityContext.getAuthentication()).thenReturn(authentication);
        // when(authentication.getPrincipal()).thenReturn(usuarioLogado); // Aqui estava o bug anterior!
        // SecurityContextHolder.setContext(securityContext);

        when(ticketRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(ticketFantasma));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            ticketService.cancelTicket(99L, usuarioLogado);
        });

        assertTrue(exception.getMessage().contains("30 minutos"));
        Mockito.verify(ticketRepository, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Deve cancelar reserva pendente sem publicar reembolso")
    void deveCancelarPendenteSemReembolso() {
        var user = new com.app.cineticket.domain.entity.User();
        user.setId(10L);
        var session = new Session();
        session.setHorarioInicio(java.time.LocalDateTime.now().plusHours(2));
        var ticket = new Ticket();
        ticket.setId(20L);
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setStatus(TicketStatus.PENDING);

        when(ticketRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(ticket));

        ticketService.cancelTicket(20L, user);

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        Mockito.verify(eventPublisher, Mockito.never())
                .publishEvent(any(com.app.cineticket.dto.request.RefundEventDTO.class));
    }

    @Test
    @DisplayName("Deve publicar reembolso apenas ao cancelar ingresso aprovado")
    void deveReembolsarIngressoAprovado() {
        var user = new com.app.cineticket.domain.entity.User();
        user.setId(10L);
        var session = new Session();
        session.setHorarioInicio(java.time.LocalDateTime.now().plusHours(2));
        var ticket = new Ticket();
        ticket.setId(21L);
        ticket.setUser(user);
        ticket.setSession(session);
        ticket.setStatus(TicketStatus.APPROVED);
        ticket.setValorPago(new BigDecimal("30.00"));

        when(ticketRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(ticket));

        ticketService.cancelTicket(21L, user);

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        Mockito.verify(eventPublisher).publishEvent(
                new com.app.cineticket.dto.request.RefundEventDTO(21L, new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("Deve rejeitar compra para sessão inativa")
    void deveRejeitarCompraParaSessaoInativa() {
        var room = new com.app.cineticket.domain.entity.Room();
        room.setId(10L);
        var seat = new com.app.cineticket.domain.entity.Seat();
        seat.setRoom(room);
        var session = new Session();
        session.setRoom(room);
        session.setAtivo(false);
        session.setHorarioInicio(java.time.LocalDateTime.now().plusHours(2));
        var request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, "tok_fake_123");

        when(seatRepository.findByIdWithLock(2L)).thenReturn(Optional.of(seat));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticketService.buyTicket(request));

        assertTrue(exception.getMessage().contains("não está ativa"));
        Mockito.verify(ticketRepository, Mockito.never()).save(any());
    }
}
