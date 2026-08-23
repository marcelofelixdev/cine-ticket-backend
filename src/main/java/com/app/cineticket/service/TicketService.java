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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.app.cineticket.messaging.PaymentProducer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final PaymentProducer paymentProducer;
    private final TicketMapper ticketMapper;

    @Transactional
    public TicketResponseDTO buyTicket(TicketRequestDTO request) {

        List<TicketStatus> statusAtivos = List.of(TicketStatus.APPROVED, TicketStatus.PENDING);

        if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(request.sessionId(), request.seatId(), statusAtivos)) {
            throw new BusinessException("OVERBOOKING: Esta cadeira já está ocupada para esta sessão.");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        Ticket ticket = ticketMapper.toEntity(request);

        ticket.setUser(loggedUser);

        var session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new BusinessException("Sessão não encontrada"));

        ticket.setSession(session);

        ticket.setSeat(seatRepository.findById(request.seatId())
                .orElseThrow(() -> new BusinessException("Cadeira não encontrada")));

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

        paymentProducer.enviarParaFilaDePagamento(new PaymentEventDTO(
                savedTicket.getId(),
                request.cartaoToken(),
                savedTicket.getValorPago()
        ));

        return ticketMapper.toResponseDTO(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getMyTickets() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        List<Ticket> meusIngressos = ticketRepository.findByUserId(loggedUser.getId());

        return meusIngressos.stream()
                .map(ticketMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelMyTicket(Long ticketId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Ingresso não encontrado"));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new BusinessException("Você não tem permissão para cancelar um ingresso que não é seu.");
        }

        LocalDateTime deadlineCancelamento = ticket.getSession().getHorarioInicio().minusMinutes(30);

        if (LocalDateTime.now().isAfter(deadlineCancelamento)) {
            throw new BusinessException("Cancelamento negado! " +
                    "Só é possível cancelar ingressos com no mínimo 30 minutos de antecedência.");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);
    }
}
