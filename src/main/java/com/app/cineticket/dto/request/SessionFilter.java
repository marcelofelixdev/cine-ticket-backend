package com.app.cineticket.dto.request;

import java.time.LocalDate;

/**
 * Filtros opcionais da listagem de sessões.
 *
 * @param movieId     só sessões deste filme
 * @param cinemaId    só sessões deste cinema
 * @param cidade      só sessões dos cinemas desta cidade
 * @param date        só sessões deste dia (horário de Brasília)
 * @param includePast inclui sessões que já começaram (padrão: false)
 * @param formato     só sessões 2D ou 3D
 * @param audio       só sessões dubladas, legendadas ou nacionais
 */
public record SessionFilter(Long movieId, Long cinemaId, String cidade, LocalDate date, boolean includePast,
                            String formato, com.app.cineticket.domain.enums.SessionAudio audio) {
}
