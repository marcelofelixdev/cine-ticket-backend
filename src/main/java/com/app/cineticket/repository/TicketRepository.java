package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsBySessionIdAndSeatId(Long sessionId, Long seatId);

}


