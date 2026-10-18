package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trava vários lugares de uma sessão de uma vez (caixa e pedido online). Os lugares são
 * travados em ordem de ID: duas operações que disputam os mesmos lugares esperam na mesma
 * ordem, sem deadlock. Quem chama precisa estar numa transação; o índice anti-overbooking
 * (idx_ticket_no_overbooking) continua sendo a última barreira ao salvar.
 */
@Component
@RequiredArgsConstructor
public class SeatAllocator {

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;

    /** Lugares travados e livres, indexados pelo ID; 409 dizendo qual lugar já está ocupado. */
    public Map<Long, Seat> lockFreeSeats(Session session, Collection<Long> seatIds) {
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessException("O mesmo lugar foi informado mais de uma vez.");
        }
        List<Seat> seats = new ArrayList<>();
        for (Long seatId : seatIds.stream().sorted().toList()) {
            Seat seat = seatRepository.findByIdWithLock(seatId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cadeira não encontrada. ID: " + seatId));
            String assento = seat.label();
            if (!seat.getRoom().getId().equals(session.getRoom().getId())) {
                throw new BusinessException("A cadeira " + assento + " não é da sala desta sessão.");
            }
            if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(session.getId(), seat.getId(),
                    TicketStatus.OCCUPYING_SEAT)) {
                throw new ConflictException("A cadeira " + assento + " já está ocupada.");
            }
            seats.add(seat);
        }
        return seats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
    }
}
