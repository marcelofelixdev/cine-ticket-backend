package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIntentId(String paymentIntentId);

    boolean existsByTicketIdAndPaymentIntentIdIsNull(Long ticketId);

    List<Refund> findByTicketId(Long ticketId);

    /** Devoluções de balcão pendentes de um cinema, com ingresso, sessão, filme, sala e assento carregados. */
    @org.springframework.data.jpa.repository.Query("""
            select r from Refund r join fetch r.ticket t join fetch t.session s join fetch s.movie
            join fetch s.room ro join fetch t.seat left join fetch t.user
            where r.status = com.app.cineticket.domain.enums.RefundStatus.PENDING
              and r.gatewayRefundId = :counterId and ro.cinema.id = :cinemaId
            order by s.horarioInicio, t.id""")
    List<Refund> findPendingCounterRefunds(@org.springframework.data.repository.query.Param("cinemaId") Long cinemaId,
                                           @org.springframework.data.repository.query.Param("counterId") String counterId);

    /** Trava a devolução pendente: dois caixas não confirmam a mesma devolução. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select r from Refund r
            where r.ticket.id = :ticketId and r.gatewayRefundId = :counterId
              and r.status = com.app.cineticket.domain.enums.RefundStatus.PENDING""")
    Optional<Refund> findPendingCounterRefundForUpdate(@org.springframework.data.repository.query.Param("ticketId") Long ticketId,
                                                       @org.springframework.data.repository.query.Param("counterId") String counterId);

    /** Linhas [status, canal do ingresso, quantidade] para as métricas de negócio. */
    @org.springframework.data.jpa.repository.Query(
            "select r.status, r.ticket.canal, count(r) from Refund r group by r.status, r.ticket.canal")
    List<Object[]> countGroupedByStatusAndChannel();
}
