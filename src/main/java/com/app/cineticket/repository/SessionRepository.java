package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByAtivoTrue();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    org.springframework.data.domain.Page<Session> findByAtivoTrue(org.springframework.data.domain.Pageable pageable);

    List<Session> findByRoomId(Long roomId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie"})
    List<Session> findByRoomIdAndAtivoTrue(Long roomId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Session s where s.id = :id")
    java.util.Optional<Session> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}


