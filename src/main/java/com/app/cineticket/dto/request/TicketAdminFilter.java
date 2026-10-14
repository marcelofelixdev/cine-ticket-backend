package com.app.cineticket.dto.request;

import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;

import java.time.LocalDate;

/**
 * Filtros da lista de ingressos do painel (todos opcionais).
 *
 * @param cinemaId  só do cinema (o gerente sempre fica no próprio cinema)
 * @param sessionId só da sessão
 * @param date      só de sessões deste dia
 * @param status    só neste status
 * @param canal     ONLINE ou BALCAO
 * @param email     parte do e-mail do cliente (conta ou e-mail informado no balcão)
 */
public record TicketAdminFilter(Long cinemaId, Long sessionId, LocalDate date, TicketStatus status,
                                TicketChannel canal, String email) {

    public TicketAdminFilter withCinema(Long scopedCinemaId) {
        return new TicketAdminFilter(scopedCinemaId, sessionId, date, status, canal, email);
    }
}
