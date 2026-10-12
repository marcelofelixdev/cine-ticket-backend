package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Session;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/** Filtros combináveis para a listagem de sessões. */
public final class SessionSpecifications {

    private SessionSpecifications() {
    }

    public static Specification<Session> active() {
        return (root, query, cb) -> cb.isTrue(root.get("ativo"));
    }

    public static Specification<Session> movieId(Long movieId) {
        return (root, query, cb) -> cb.equal(root.get("movie").get("id"), movieId);
    }

    public static Specification<Session> cinemaId(Long cinemaId) {
        return (root, query, cb) -> cb.equal(root.get("room").get("cinema").get("id"), cinemaId);
    }

    /** Sessões que começam a partir deste instante (inclusive). */
    public static Specification<Session> startsFrom(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("horarioInicio"), from);
    }

    /** Sessões que começam antes deste instante (exclusivo). */
    public static Specification<Session> startsBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThan(root.get("horarioInicio"), to);
    }
}
