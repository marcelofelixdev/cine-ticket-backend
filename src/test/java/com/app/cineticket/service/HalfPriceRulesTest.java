package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Meia-entrada: idoso pela data de nascimento, estudante pela carteira e cota de 40% por sessão. */
@ExtendWith(MockitoExtension.class)
class HalfPriceRulesTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private TicketMapper ticketMapper;
    @Mock private OutboxService outboxService;
    @Mock private PdfService pdfService;

    private TicketService ticketService;

    private Session session;
    private User buyer;

    @BeforeEach
    void createService() {
        // HalfPricePolicy real (com o repositório simulado): a regra da cota é a que está sendo testada.
        ticketService = new TicketService(ticketRepository, sessionRepository, seatRepository, outboxService,
                ticketMapper, pdfService, new HalfPricePolicy(ticketRepository));
    }

    @Test
    void idosoSemDataDeNascimentoNaoCompraMeia() {
        prepare(TicketType.MEIA_IDOSO, null);

        BusinessException e = assertThrows(BusinessException.class, () -> buy(TicketType.MEIA_IDOSO, null));

        assertTrue(e.getMessage().contains("data de nascimento"));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void quemTem59AnosNaDataDaSessaoNaoEIdoso() {
        prepare(TicketType.MEIA_IDOSO, null);
        buyer.setDataNascimento(session.getHorarioInicio().toLocalDate().minusYears(60).plusDays(1));

        assertThrows(BusinessException.class, () -> buy(TicketType.MEIA_IDOSO, null));
    }

    @Test
    void quemCompleta60AnosAteASessaoPagaMeia() {
        prepare(TicketType.MEIA_IDOSO, 0L);
        buyer.setDataNascimento(session.getHorarioInicio().toLocalDate().minusYears(60));

        buy(TicketType.MEIA_IDOSO, null);

        Ticket saved = captureSaved();
        assertEquals(0, new BigDecimal("15.00").compareTo(saved.getValorPago()));
    }

    @Test
    void estudanteSemCarteiraNaoCompraMeia() {
        prepare(TicketType.MEIA_ESTUDANTE, null);

        assertThrows(BusinessException.class, () -> buy(TicketType.MEIA_ESTUDANTE, "  "));
    }

    @Test
    void numeroDaCarteiraFicaGravadoNoIngresso() {
        prepare(TicketType.MEIA_ESTUDANTE, 0L);

        buy(TicketType.MEIA_ESTUDANTE, " cie-2026-77 ");

        assertEquals("CIE-2026-77", captureSaved().getDocumentoMeia());
    }

    @Test
    void cotaDe40PorCentoEsgotadaBloqueiaMeia() {
        prepare(TicketType.MEIA_ESTUDANTE, 4L); // sala de 10 lugares: cota de 4

        ConflictException e = assertThrows(ConflictException.class,
                () -> buy(TicketType.MEIA_ESTUDANTE, "CIE-2026-77"));

        assertTrue(e.getMessage().contains("Cota de meia-entrada esgotada"));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void inteiraNaoConsultaACota() {
        prepare(TicketType.INTEIRA, null);

        buy(TicketType.INTEIRA, "ignorado");

        assertEquals(null, captureSaved().getDocumentoMeia());
        verify(ticketRepository, never()).countBySessionIdAndTicketTypeInAndStatusIn(anyLong(), anyCollection(), anyCollection());
    }

    /** @param halfPriceSold meias já vendidas na sessão, ou null se a cota não deve ser consultada */
    private void prepare(TicketType type, Long halfPriceSold) {
        Room room = new Room();
        room.setId(10L);
        room.setCapacidade(10);
        Seat seat = new Seat();
        seat.setId(2L);
        seat.setRoom(room);
        session = new Session();
        session.setId(1L);
        session.setRoom(room);
        session.setAtivo(true);
        session.setValorBase(new BigDecimal("30.00"));
        session.setHorarioInicio(LocalDateTime.now().plusDays(3));
        buyer = new User();
        buyer.setId(7L);

        when(seatRepository.findByIdWithLock(2L)).thenReturn(Optional.of(seat));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(eq(1L), eq(2L), anyList())).thenReturn(false);
        when(ticketRepository.countByUserIdAndStatus(eq(7L), any())).thenReturn(0L);
        if (halfPriceSold != null) {
            when(sessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(session));
            when(ticketRepository.countBySessionIdAndTicketTypeInAndStatusIn(eq(1L), anyCollection(), anyCollection()))
                    .thenReturn(halfPriceSold);
        }
        if (type == TicketType.INTEIRA || (halfPriceSold != null && halfPriceSold < 4)) {
            when(ticketMapper.toEntity(any())).thenReturn(new Ticket());
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        ReflectionTestUtils.setField(ticketService, "paymentsEnabled", true);
    }

    private void buy(TicketType type, String documento) {
        ticketService.buyTicket(new TicketRequestDTO(1L, 2L, type, documento), buyer);
    }

    private Ticket captureSaved() {
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        return captor.getValue();
    }
}
