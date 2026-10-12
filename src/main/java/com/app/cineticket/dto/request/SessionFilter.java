package com.app.cineticket.dto.request;

import java.time.LocalDate;

/**
 * Filtros opcionais da listagem de sessões.
 *
 * @param movieId     só sessões deste filme
 * @param cinemaId    só sessões deste cinema
 * @param date        só sessões deste dia (horário de Brasília)
 * @param includePast inclui sessões que já começaram (padrão: false)
 */
public record SessionFilter(Long movieId, Long cinemaId, LocalDate date, boolean includePast) {
}
