package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Session> {

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    org.springframework.data.domain.Page<Session> findAll(
            org.springframework.data.jpa.domain.Specification<Session> spec,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    java.util.Optional<Session> findWithDetailsById(Long id);

    List<Session> findByAtivoTrue();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    org.springframework.data.domain.Page<Session> findByAtivoTrue(org.springframework.data.domain.Pageable pageable);

    List<Session> findByRoomId(Long roomId);

    boolean existsByRoomId(Long roomId);

    boolean existsByRoomCinemaId(Long cinemaId);

    boolean existsByRoomIdAndHorarioInicioBetween(Long roomId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"movie"})
    List<Session> findByRoomIdAndAtivoTrue(Long roomId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Session s where s.id = :id")
    java.util.Optional<Session> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
}


