package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CinemaMovieRepository extends JpaRepository<CinemaMovie, Long> {

    Optional<CinemaMovie> findByCinemaIdAndMovieId(Long cinemaId, Long movieId);

    boolean existsByCinemaIdAndMovieIdAndStatus(Long cinemaId, Long movieId, ProgramacaoStatus status);

    /** Programação de um cinema, com o filme carregado. */
    @Query("""
            select cm from CinemaMovie cm join fetch cm.movie m
            where cm.cinema.id = :cinemaId order by cm.status, m.titulo""")
    List<CinemaMovie> findByCinemaWithMovie(@Param("cinemaId") Long cinemaId);

    /** Programação dos filmes informados, com o cinema carregado. */
    @Query("""
            select cm from CinemaMovie cm join fetch cm.cinema c
            where cm.movie.id in :movieIds order by c.nome""")
    List<CinemaMovie> findByMovieIdsWithCinema(@Param("movieIds") Collection<Long> movieIds);
}
