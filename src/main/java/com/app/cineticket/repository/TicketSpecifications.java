package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.TicketAdminFilter;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros da lista de ingressos do painel. */
public final class TicketSpecifications {

    private static final String SESSION = "session";

    private TicketSpecifications() {
    }

    public static Specification<Ticket> matching(TicketAdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.cinemaId() != null) {
                predicates.add(cb.equal(root.get(SESSION).get("room").get("cinema").get("id"), filter.cinemaId()));
            }
            if (filter.sessionId() != null) {
                predicates.add(cb.equal(root.get(SESSION).get("id"), filter.sessionId()));
            }
            if (filter.date() != null) {
                predicates.add(cb.between(root.get(SESSION).get("horarioInicio"),
                        filter.date().atStartOfDay(), filter.date().plusDays(1).atStartOfDay().minusNanos(1)));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.canal() != null) {
                predicates.add(cb.equal(root.get("canal"), filter.canal()));
            }
            if (filter.email() != null && !filter.email().isBlank()) {
                String pattern = "%" + filter.email().trim().toLowerCase(Locale.ROOT) + "%";
                Join<Ticket, User> user = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("email")), pattern),
                        cb.like(cb.lower(root.get("clienteEmail")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
