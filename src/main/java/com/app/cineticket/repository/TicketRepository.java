package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsBySessionIdAndSeatIdAndStatusIn(Long sessionId, Long seatId, List<TicketStatus> statuses);

    List<Ticket> findBySessionId(Long sessionId);

    List<Ticket> findByUserId(Long userId);

}