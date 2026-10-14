package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.MovieRequest;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRequestRepository extends JpaRepository<MovieRequest, Long> {

    boolean existsByCinemaIdAndTmdbIdAndStatus(Long cinemaId, Long tmdbId, MovieRequestStatus status);

    @Query("""
            select r from MovieRequest r join fetch r.cinema left join fetch r.requestedBy
            where r.cinema.id = :cinemaId order by r.createdAt desc, r.id desc""")
    List<MovieRequest> findByCinema(@Param("cinemaId") Long cinemaId);

    @Query("""
            select r from MovieRequest r join fetch r.cinema left join fetch r.requestedBy
            order by r.createdAt desc, r.id desc""")
    List<MovieRequest> findAllWithCinema();

    @Query("""
            select r from MovieRequest r join fetch r.cinema left join fetch r.requestedBy
            where r.status = :status order by r.createdAt desc, r.id desc""")
    List<MovieRequest> findByStatusWithCinema(@Param("status") MovieRequestStatus status);

    /** Trava o pedido para dois admins não decidirem o mesmo pedido ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MovieRequest r where r.id = :id")
    Optional<MovieRequest> findByIdForUpdate(@Param("id") Long id);
}
