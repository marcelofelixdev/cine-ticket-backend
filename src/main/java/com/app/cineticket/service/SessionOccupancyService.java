package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ocupação das sessões em uma consulta só (lista do site, do gerente e do caixa) e o que
 * se deriva dela: lugares livres e meias que ainda cabem na cota.
 */
@Service
@RequiredArgsConstructor
public class SessionOccupancyService {

    private final TicketRepository ticketRepository;
    private final HalfPricePolicy halfPricePolicy;

    /** Sessão sem ingresso não aparece no mapa: use {@code getOrDefault(id, SessionOccupancy.EMPTY)}. */
    @Transactional(readOnly = true)
    public Map<Long, SessionOccupancy> bySession(Collection<Long> sessionIds) {
        Map<Long, SessionOccupancy> result = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }
        for (Object[] row : ticketRepository.countBySessionTypeAndStatus(sessionIds, TicketStatus.OCCUPYING_SEAT)) {
            Long sessionId = (Long) row[0];
            TicketType type = (TicketType) row[1];
            TicketStatus status = (TicketStatus) row[2];
            long count = (Long) row[3];
            BigDecimal paid = status == TicketStatus.APPROVED && row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            result.merge(sessionId,
                    new SessionOccupancy(count, HalfPricePolicy.isHalfPrice(type) ? count : 0, paid),
                    SessionOccupancy::plus);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SessionOccupancy of(Long sessionId) {
        return bySession(List.of(sessionId)).getOrDefault(sessionId, SessionOccupancy.EMPTY);
    }

    public int lugaresLivres(Session session, SessionOccupancy occupancy) {
        return (int) Math.max(0, HalfPricePolicy.capacityOf(session) - occupancy.ocupados());
    }

    public int meiasRestantes(Session session, SessionOccupancy occupancy) {
        return (int) halfPricePolicy.remaining(session, occupancy.meias());
    }
}
