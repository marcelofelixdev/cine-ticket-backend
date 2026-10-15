package com.app.cineticket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponseDTO (
        Long id,
        LocalDateTime horarioInicio,
        BigDecimal valorBase,
        /** Preço da meia-entrada (metade do valor da sessão). */
        BigDecimal valorMeia,
        Long movieId,
        String movieTitulo,
        Long roomId,
        String roomNome,
        Long cinemaId,
        String cinemaNome,
        String formato,
        com.app.cineticket.domain.enums.SessionAudio audio,
        /** Lugares livres agora. */
        Integer lugaresLivres,
        /** Meias que ainda cabem na cota da sessão. */
        Integer meiasRestantes
) {

    public SessionResponseDTO withAvailability(int livres, int meias) {
        return new SessionResponseDTO(id, horarioInicio, valorBase, valorMeia, movieId, movieTitulo, roomId, roomNome,
                cinemaId, cinemaNome, formato, audio, livres, meias);
    }
}
