package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.TicketOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TicketOrder o where o.id = :id")
    Optional<TicketOrder> findByIdForUpdate(@Param("id") Long id);

    /** Pedido com sessão, filme, sala, ingressos e lugares, para a resposta da API. */
    @EntityGraph(attributePaths = {"user", "session.movie", "session.room", "tickets", "tickets.seat"})
    Optional<TicketOrder> findWithDetailsById(Long id);

    /** Expira os pedidos pendentes vencidos (os ingressos deles expiram pela mesma rotina). */
    @Modifying
    @Query("update TicketOrder o set o.status = com.app.cineticket.domain.enums.OrderStatus.EXPIRED, "
            + "o.updatedAt = :now "
            + "where o.status = com.app.cineticket.domain.enums.OrderStatus.PENDING and o.expiresAt < :now")
    int expirePendingOrders(@Param("now") LocalDateTime now);
}
