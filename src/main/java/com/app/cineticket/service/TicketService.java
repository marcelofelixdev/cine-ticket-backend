package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final TicketMapper ticketMapper;
    private final com.app.cineticket.service.PdfService pdfService;

    @Transactional(readOnly = true)
    public byte[] downloadPdf(Long ticketId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Ingresso não encontrado"));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new BusinessException("IDOR PREVENTED: Você não tem permissão para baixar o ingresso de outra pessoa.");
        }

        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso não aprovado ainda.");
        }

        return pdfService.generateTicketPdf(ticket);
    }

    @Transactional
    public TicketResponseDTO buyTicket(TicketRequestDTO request) {
        // 1. Bloqueia a cadeira no banco (Trava Pessimista) para evitar Overbooking
        var seat = seatRepository.findByIdWithLock(request.seatId())
                .orElseThrow(() -> new BusinessException("Cadeira não encontrada"));

        // 2. Busca a Sessão
        var session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new BusinessException("Sessão não encontrada"));

        // 3. Valida se a Cadeira pertence à Sala da Sessão (AUD-023)
        if (!seat.getRoom().getId().equals(session.getRoom().getId())) {
            throw new BusinessException("FRAUDE: A cadeira solicitada não pertence à sala desta sessão.");
        }

        // 4. Verifica se a Cadeira já foi vendida (agora seguro pela trava da cadeira)
        List<TicketStatus> statusAtivos = List.of(TicketStatus.APPROVED, TicketStatus.PENDING);
        if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(request.sessionId(), request.seatId(), statusAtivos)) {
            throw new BusinessException("OVERBOOKING: Esta cadeira já está ocupada para esta sessão.");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        Ticket ticket = ticketMapper.toEntity(request);
        ticket.setUser(loggedUser);
        ticket.setSession(session);
        ticket.setSeat(seat);

        BigDecimal valorSessao = session.getValorBase();

        if (request.ticketType() == TicketType.INTEIRA) {
            ticket.setValorPago(valorSessao);
        } else {
            ticket.setValorPago(valorSessao.divide(new BigDecimal("2"),
                    RoundingMode.HALF_UP));
        }

        ticket.setTicketType(request.ticketType());

        ticket.setStatus(TicketStatus.PENDING);

        Ticket savedTicket = ticketRepository.save(ticket);

        eventPublisher.publishEvent(new PaymentEventDTO(
                savedTicket.getId(),
                request.cartaoToken(),
                savedTicket.getValorPago()
        ));

        return ticketMapper.toResponseDTO(savedTicket);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponseDTO> findMyTickets(User loggedUser, org.springframework.data.domain.Pageable pageable) {
        return ticketRepository.findByUserId(loggedUser.getId(), pageable)
                .map(ticketMapper::toResponseDTO);
    }

    @Transactional
    public TicketResponseDTO cancelTicket(Long id, User loggedUser) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Ingresso não encontrado"));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new BusinessException("Ingresso não pertence ao usuário logado");
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new BusinessException("Ingresso já está cancelado");
        }

        LocalDateTime deadlineCancelamento = ticket.getSession().getHorarioInicio().minusMinutes(30);

        if (LocalDateTime.now().isAfter(deadlineCancelamento)) {
            throw new BusinessException("Cancelamento negado! " +
                    "Só é possível cancelar ingressos com no mínimo 30 minutos de antecedência.");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        log.info("Ticket {} cancelado. Preparando para notificar sistema de reembolso...", ticket.getId());

        eventPublisher.publishEvent(new com.app.cineticket.dto.request.RefundEventDTO(
                ticket.getId(),
                ticket.getValorPago()
        ));

        return ticketMapper.toResponseDTO(ticket);
    }
}
