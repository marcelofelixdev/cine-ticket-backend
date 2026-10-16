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
import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Ticket> {

    boolean existsBySessionIdAndSeatIdAndStatusIn(Long sessionId, Long seatId, List<TicketStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "session.movie", "session.room", "seat"})
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.session.id = :sessionId")
    List<Ticket> findBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("update Ticket t set t.status = com.app.cineticket.domain.enums.TicketStatus.APPROVED, " +
            "t.paymentIntentId = :paymentIntentId " +
            "where t.id = :ticketId and t.status = com.app.cineticket.domain.enums.TicketStatus.PENDING " +
            "and t.valorPago = :expectedAmount")
    int approvePendingPayment(@Param("ticketId") Long ticketId,
                              @Param("expectedAmount") BigDecimal expectedAmount,
                              @Param("paymentIntentId") String paymentIntentId);

    /** Aprova os ingressos ainda pendentes de um pedido com o pagamento que cobriu o pedido inteiro. */
    @Modifying
    @Query("update Ticket t set t.status = com.app.cineticket.domain.enums.TicketStatus.APPROVED, " +
            "t.paymentIntentId = :paymentIntentId " +
            "where t.ticketOrder.id = :orderId and t.status = com.app.cineticket.domain.enums.TicketStatus.PENDING")
    int approvePendingOrderTickets(@Param("orderId") Long orderId, @Param("paymentIntentId") String paymentIntentId);

    long countByUserIdAndStatus(Long userId, TicketStatus status);

    /** Linhas [status, tipo, quantidade] para as métricas de negócio. */
    @Query("select t.status, t.ticketType, count(t) from Ticket t group by t.status, t.ticketType")
    List<Object[]> countGroupedByStatusAndType();

    long countByUsedAtIsNotNull();

    /** Linhas [sessão, tipo, status, quantidade, soma do valor pago] das sessões pedidas. */
    @Query("select t.session.id, t.ticketType, t.status, count(t), sum(t.valorPago) from Ticket t "
            + "where t.session.id in :sessionIds and t.status in :statuses "
            + "group by t.session.id, t.ticketType, t.status")
    List<Object[]> countBySessionTypeAndStatus(@Param("sessionIds") java.util.Collection<Long> sessionIds,
                                               @Param("statuses") java.util.Collection<TicketStatus> statuses);

    long countBySessionIdAndStatusIn(Long sessionId, java.util.Collection<TicketStatus> statuses);

    long countBySessionIdAndTicketTypeInAndStatusIn(Long sessionId,
                                                     java.util.Collection<com.app.cineticket.domain.enums.TicketType> types,
                                                     java.util.Collection<TicketStatus> statuses);

    @Query("select t.seat.id from Ticket t where t.session.id = :sessionId and t.status in :statuses")
    List<Long> findSeatIdsBySessionIdAndStatusIn(@Param("sessionId") Long sessionId,
                                                 @Param("statuses") List<TicketStatus> statuses);

    @Modifying
    @Query("update Ticket t set t.status = com.app.cineticket.domain.enums.TicketStatus.EXPIRED, " +
            "t.updatedAt = :now " +
            "where t.status = com.app.cineticket.domain.enums.TicketStatus.PENDING " +
            "and t.expiresAt < :now")
    int expirePendingTickets(@Param("now") LocalDateTime now);

    /** Lista do painel com o que o DTO mostra já carregado. */
    @EntityGraph(attributePaths = {"session.movie", "session.room", "seat", "user", "vendidoPor"})
    org.springframework.data.domain.Page<Ticket> findAll(
            org.springframework.data.jpa.domain.Specification<Ticket> spec, org.springframework.data.domain.Pageable pageable);

    /** Ingressos de uma sessão para o resumo (status, canal, forma, tipo, valor, uso). */
    List<Ticket> findBySessionId(Long sessionId);

    @EntityGraph(attributePaths = {"session.movie", "session.room", "seat"})
    org.springframework.data.domain.Page<Ticket> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);

}
