package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private OutboxService outboxService;

    @Mock
    private PdfService pdfService;

    @InjectMocks
    private TicketService ticketService;

    @Test
    @DisplayName("Deve lançar BusinessException ao tentar comprar uma cadeira já vendida")
    void deveLancarErroQuandoCadeiraJaVendida() {
        TicketRequestDTO request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, null);

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

        assertEquals("Esta cadeira já está ocupada para esta sessão.", exception.getMessage());

        verify(ticketRepository, never()).save(any());
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
        verify(ticketRepository, never()).save(any());
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
        verify(outboxService, never())
                .refundRequested(any(com.app.cineticket.dto.request.RefundEventDTO.class));
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
        ticket.setPaymentIntentId("pi_test_21");

        when(ticketRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(ticket));

        ticketService.cancelTicket(21L, user);

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        verify(outboxService).refundRequested(
                new com.app.cineticket.dto.request.RefundEventDTO(21L, "pi_test_21", new BigDecimal("30.00")));
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
        var request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, null);

        when(seatRepository.findByIdWithLock(2L)).thenReturn(Optional.of(seat));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticketService.buyTicket(request));

        assertTrue(exception.getMessage().contains("não está ativa"));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve reservar o assento com prazo de pagamento de 10 minutos")
    void deveDefinirPrazoDeExpiracaoNaCompra() {
        TicketRequestDTO request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, null);
        prepararCompraValida(request, 0L);
        when(ticketMapper.toEntity(request)).thenReturn(new Ticket());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        java.time.LocalDateTime antes = java.time.LocalDateTime.now();
        ticketService.buyTicket(request);

        org.mockito.ArgumentCaptor<Ticket> captor = org.mockito.ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket salvo = captor.getValue();
        assertEquals(TicketStatus.PENDING, salvo.getStatus());
        assertTrue(!salvo.getExpiresAt().isBefore(antes.plusMinutes(10)));
        assertTrue(salvo.getExpiresAt().isBefore(antes.plusMinutes(11)));
    }

    @Test
    @DisplayName("Deve bloquear nova reserva quando o usuário atingiu o limite de pendentes")
    void deveBloquearCompraAcimaDoLimiteDePendentes() {
        TicketRequestDTO request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, null);
        prepararCompraValida(request, 4L);

        BusinessException exception = assertThrows(BusinessException.class, () -> ticketService.buyTicket(request));

        assertTrue(exception.getMessage().contains("aguardando pagamento"));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não deve cancelar um ingresso expirado")
    void naoDeveCancelarIngressoExpirado() {
        com.app.cineticket.domain.entity.User dono = new com.app.cineticket.domain.entity.User();
        dono.setId(7L);
        Ticket ticket = new Ticket();
        ticket.setId(30L);
        ticket.setUser(dono);
        ticket.setStatus(TicketStatus.EXPIRED);
        when(ticketRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ticket));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ticketService.cancelTicket(30L, dono));

        assertEquals("O ingresso expirou por falta de pagamento.", exception.getMessage());
        verifyNoInteractions(outboxService);
    }

    private void prepararCompraValida(TicketRequestDTO request, long pendentesDoUsuario) {
        com.app.cineticket.domain.entity.Room room = new com.app.cineticket.domain.entity.Room();
        room.setId(10L);
        com.app.cineticket.domain.entity.Seat seat = new com.app.cineticket.domain.entity.Seat();
        seat.setId(request.seatId());
        seat.setRoom(room);
        com.app.cineticket.domain.entity.Session session = new com.app.cineticket.domain.entity.Session();
        session.setId(request.sessionId());
        session.setRoom(room);
        session.setAtivo(true);
        session.setValorBase(new BigDecimal("30.00"));
        session.setHorarioInicio(java.time.LocalDateTime.now().plusHours(2));
        com.app.cineticket.domain.entity.User comprador = new com.app.cineticket.domain.entity.User();
        comprador.setId(7L);

        when(seatRepository.findByIdWithLock(request.seatId())).thenReturn(Optional.of(seat));
        when(sessionRepository.findById(request.sessionId())).thenReturn(Optional.of(session));
        when(ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(eq(request.sessionId()), eq(request.seatId()), anyList()))
                .thenReturn(false);
        when(ticketRepository.countByUserIdAndStatus(7L, TicketStatus.PENDING)).thenReturn(pendentesDoUsuario);
        org.springframework.test.util.ReflectionTestUtils.setField(ticketService, "paymentsEnabled", true);

        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        comprador, null, java.util.List.of()));
    }

    @org.junit.jupiter.api.AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }
}
