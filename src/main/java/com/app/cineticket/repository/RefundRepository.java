package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIntentId(String paymentIntentId);

    boolean existsByTicketIdAndPaymentIntentIdIsNull(Long ticketId);

    List<Refund> findByTicketId(Long ticketId);
}
