package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
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

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final TicketMapper ticketMapper;

    @Transactional
    public TicketResponseDTO buyTicket(TicketRequestDTO request) {

        if (ticketRepository.existsBySessionIdAndSeatId(request.sessionId(), request.seatId())) {
            throw new BusinessException("OVERBOOKING: Esta cadeira já foi vendida para está sessão");
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

        ticket.setValorPago(session.getValorBase());

        ticket.setStatus(TicketStatus.APPROVED);

        return ticketMapper.toResponseDTO(ticketRepository.save(ticket));
    }
}
