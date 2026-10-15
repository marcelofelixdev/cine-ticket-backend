package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.SessionAudio;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros da listagem pública de filmes. */
public final class MovieSpecifications {

    private static final String DATA_ESTREIA = "dataEstreia";
    private static final String CINEMA = "cinema";

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

    /**
     * Tem sessão ativa a partir de {@code from} com o formato e o áudio pedidos (nulos = qualquer),
     * no cinema ou na cidade informados.
     */
    public static Specification<Movie> comSessao(Long cinemaId, String cidade, String formato, SessionAudio audio,
                                                 LocalDateTime from) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Session> session = sub.from(Session.class);
            Path<Object> cinema = session.get("room").get(CINEMA);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(session.get("movie"), root));
            predicates.add(cb.isTrue(session.get("ativo")));
            predicates.add(cb.greaterThanOrEqualTo(session.get("horarioInicio"), from));
            if (formato != null) {
                predicates.add(cb.equal(session.get("formato"), formato));
            }
            if (audio != null) {
                predicates.add(cb.equal(session.get("audio"), audio));
            }
            if (cinemaId != null) {
                predicates.add(cb.equal(cinema.get("id"), cinemaId));
            }
            if (cidade != null && !cidade.isBlank()) {
                predicates.add(sameCity(cb, cinema, cidade));
            }
            sub.select(session.get("id")).where(predicates.toArray(new Predicate[0]));
            return cb.exists(sub);
        };
    }

    public static Specification<Movie> classificacao(String classificacao) {
        return (root, query, cb) -> cb.equal(root.get("classificacao"), classificacao);
    }

    private static Predicate sameCity(CriteriaBuilder cb, Path<?> cinema, String cidade) {
        return cb.equal(cb.lower(cinema.get("cidade")), cidade.trim().toLowerCase(Locale.ROOT));
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
            predicates.add(cb.equal(cinemaMovie.get(CINEMA).get("id"), cinemaId));
        }
        if (cidade != null && !cidade.isBlank()) {
            predicates.add(sameCity(cb, cinemaMovie.get(CINEMA), cidade));
        }
        sub.select(cinemaMovie.get("id")).where(predicates.toArray(new Predicate[0]));
        return sub;
    }
}
