package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros da listagem pública de filmes. */
public final class MovieSpecifications {

    private MovieSpecifications() {
    }

    /** Filmes ativos em algum cinema (ou no cinema informado). */
    public static Specification<Movie> emCartaz(Long cinemaId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<CinemaMovie> cinemaMovie = sub.from(CinemaMovie.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cinemaMovie.get("movie"), root));
            predicates.add(cb.equal(cinemaMovie.get("status"), ProgramacaoStatus.ATIVO));
            if (cinemaId != null) {
                predicates.add(cb.equal(cinemaMovie.get("cinema").get("id"), cinemaId));
            }
            sub.select(cinemaMovie.get("id")).where(predicates.toArray(new Predicate[0]));
            return cb.exists(sub);
        };
    }
}
