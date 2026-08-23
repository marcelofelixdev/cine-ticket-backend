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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
    private PaymentProducer paymentProducer;

    @InjectMocks
    private TicketService ticketService;

    @Test
    @DisplayName("Deve lançar BusinessException ao tentar comprar uma cadeira já vendida")
    void deveLancarErroQuandoCadeiraJaVendida() {
        TicketRequestDTO request = new TicketRequestDTO(1L, 2L, TicketType.INTEIRA, "tok_fake_123");

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

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(usuarioLogado); // Aqui estava o bug anterior!
        SecurityContextHolder.setContext(securityContext);

        when(ticketRepository.findById(99L)).thenReturn(Optional.of(ticketFantasma));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            ticketService.cancelMyTicket(99L);
        });

        assertEquals("Cancelamento negado! Só é possível cancelar ingressos com no mínimo 30 minutos de antecedência.", exception.getMessage());
        Mockito.verify(ticketRepository, Mockito.never()).save(any());
    }
}
