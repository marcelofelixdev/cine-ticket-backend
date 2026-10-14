package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.TicketAdminFilter;
import com.app.cineticket.dto.response.SessionSummaryDTO;
import com.app.cineticket.dto.response.TicketAdminDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.TicketSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Administração de ingressos no painel: lista com filtros, resumo por sessão e cancelamento
 * de venda de balcão. {@code cinemaScope} limita ao cinema do gerente; null = rede toda (admin).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAdminService {

    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final TicketMapper ticketMapper;
    private final CounterRefundService counterRefundService;

    @Transactional(readOnly = true)
    public Page<TicketAdminDTO> list(TicketAdminFilter filter, Long cinemaScope, Pageable pageable) {
        TicketAdminFilter scoped = cinemaScope == null ? filter : filter.withCinema(cinemaScope);
        return ticketRepository.findAll(TicketSpecifications.matching(scoped), pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public SessionSummaryDTO summary(Long sessionId, Long cinemaScope) {
        Session session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
        CinemaScope.requireWithin(cinemaScope, session.getRoom().getCinema().getId(), "Esta sessão é de outro cinema.");

        List<Ticket> tickets = ticketRepository.findBySessionId(sessionId);
        List<Ticket> approved = tickets.stream().filter(t -> t.getStatus() == TicketStatus.APPROVED).toList();
        long reserved = tickets.stream().filter(t -> t.getStatus() == TicketStatus.PENDING).count();
        int capacity = session.getRoom().getCapacidade() == null ? 0 : session.getRoom().getCapacidade();

        Map<FormaPagamento, BigDecimal> counterByForm = new EnumMap<>(FormaPagamento.class);
        approved.stream()
                .filter(t -> t.getCanal() == TicketChannel.BALCAO)
                .forEach(t -> counterByForm.merge(t.getFormaPagamento(), t.getValorPago(), BigDecimal::add));

        return new SessionSummaryDTO(
                session.getId(),
                session.getMovie().getTitulo(),
                session.getRoom().getNome(),
                session.getHorarioInicio(),
                Boolean.TRUE.equals(session.getAtivo()),
                capacity,
                approved.size(),
                reserved,
                Math.max(0, capacity - approved.size() - reserved),
                approved.stream().filter(t -> HalfPricePolicy.isHalfPrice(t.getTicketType())).count(),
                approved.stream().filter(t -> t.getUsedAt() != null).count(),
                revenue(approved, t -> true),
                revenue(approved, t -> t.getCanal() == TicketChannel.ONLINE),
                revenue(approved, t -> t.getCanal() == TicketChannel.BALCAO),
                counterByForm);
    }

    /**
     * Gerente cancela uma venda de balcão do próprio cinema até o início da sessão e devolve
     * o valor na hora: o reembolso já fica registrado como feito (REFUNDED).
     */
    @Transactional
    public TicketResponseDTO cancelCounterSale(Long ticketId, User manager) {
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));
        CinemaScope.requireSameCinema(manager, ticket.getSession().getRoom().getCinema().getId(),
                "Este ingresso é de outro cinema.");
        if (ticket.getCanal() != TicketChannel.BALCAO) {
            throw new BusinessException("Ingresso comprado online: o cancelamento é feito pelo cliente no site "
                    + "ou pelo cancelamento emergencial da sessão.");
        }
        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new ConflictException("O ingresso já está cancelado.");
        }
        if (ticket.getUsedAt() != null) {
            throw new ConflictException("Ingresso já utilizado na entrada não pode ser cancelado.");
        }
        if (!LocalDateTime.now(AppTimeZone.ZONE).isBefore(ticket.getSession().getHorarioInicio())) {
            throw new BusinessException("A sessão já começou: o ingresso não pode mais ser cancelado.");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);
        counterRefundService.refundNow(ticket, manager);
        log.info("[CAIXA] Ingresso {} cancelado por {}; R$ {} devolvido no caixa.",
                ticketId, manager.getEmail(), ticket.getValorPago());
        return ticketMapper.toResponseDTO(ticket);
    }

    private TicketAdminDTO toDto(Ticket ticket) {
        Session session = ticket.getSession();
        return new TicketAdminDTO(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getCanal(),
                ticket.getFormaPagamento(),
                session.getId(),
                session.getMovie().getTitulo(),
                session.getRoom().getNome(),
                session.getHorarioInicio(),
                ticket.getSeat().label(),
                ticket.getTicketType(),
                ticket.getValorPago(),
                ticket.getUser() != null ? ticket.getUser().getEmail() : ticket.getClienteEmail(),
                ticket.getVendidoPor() == null ? null : ticket.getVendidoPor().getEmail(),
                ticketMapper.refundStatus(ticket),
                ticket.getUsedAt(),
                ticket.getCreatedAt());
    }

    private static BigDecimal revenue(List<Ticket> approved, Predicate<Ticket> filter) {
        return approved.stream().filter(filter).map(Ticket::getValorPago).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
