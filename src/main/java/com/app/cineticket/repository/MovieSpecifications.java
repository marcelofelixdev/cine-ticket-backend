package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filtros da listagem pública de filmes. */
public final class MovieSpecifications {

    private static final String DATA_ESTREIA = "dataEstreia";

    private MovieSpecifications() {
    }

    /**
     * Em cartaz: ativo em algum cinema (ou no cinema / na cidade informados) e já estreado
     * (sem data de estreia ou com estreia até hoje).
     */
    public static Specification<Movie> emCartaz(Long cinemaId, String cidade, LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.exists(programacao(root, query, cb, true, cinemaId, cidade)),
                cb.or(cb.isNull(root.get(DATA_ESTREIA)), cb.lessThanOrEqualTo(root.get(DATA_ESTREIA), today)));
    }

    /** Em breve: na programação de algum cinema (ativo ou aguardando) e com estreia depois de hoje. */
    public static Specification<Movie> emBreve(String cidade, LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.exists(programacao(root, query, cb, false, null, cidade)),
                cb.greaterThan(root.get(DATA_ESTREIA), today));
    }

    private static Subquery<Long> programacao(Root<Movie> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                              boolean onlyActive, Long cinemaId, String cidade) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<CinemaMovie> cinemaMovie = sub.from(CinemaMovie.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(cinemaMovie.get("movie"), root));
        if (onlyActive) {
            predicates.add(cb.equal(cinemaMovie.get("status"), ProgramacaoStatus.ATIVO));
        }
        if (cinemaId != null) {
            predicates.add(cb.equal(cinemaMovie.get("cinema").get("id"), cinemaId));
        }
        if (cidade != null && !cidade.isBlank()) {
            predicates.add(cb.equal(cb.lower(cinemaMovie.get("cinema").get("cidade")), cidade.trim().toLowerCase()));
        }
        sub.select(cinemaMovie.get("id")).where(predicates.toArray(new Predicate[0]));
        return sub;
    }
}
