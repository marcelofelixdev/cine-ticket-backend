package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsBySessionIdAndSeatIdAndStatusIn(Long sessionId, Long seatId, List<TicketStatus> statuses);

    List<Ticket> findBySessionId(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "session.movie", "session.room", "seat"})
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.session.id = :sessionId")
    List<Ticket> findBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("update Ticket t set t.status = com.app.cineticket.domain.enums.TicketStatus.APPROVED " +
            "where t.id = :ticketId and t.status = com.app.cineticket.domain.enums.TicketStatus.PENDING " +
            "and t.valorPago = :expectedAmount")
    int approvePendingPayment(@Param("ticketId") Long ticketId,
                              @Param("expectedAmount") BigDecimal expectedAmount);

    @EntityGraph(attributePaths = {"session.movie", "session.room", "seat"})
    org.springframework.data.domain.Page<Ticket> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);

}
