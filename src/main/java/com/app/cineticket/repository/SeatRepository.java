package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Seat s WHERE s.id = :id")
    java.util.Optional<Seat> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}


