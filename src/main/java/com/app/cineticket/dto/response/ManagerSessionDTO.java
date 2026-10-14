package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.SessionAudio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sessão do cinema do gerente, com a ocupação (lista do painel e do caixa). */
public record ManagerSessionDTO(
        Long id,
        LocalDateTime horarioInicio,
        LocalDateTime horarioFim,
        Long movieId,
        String movieTitulo,
        String classificacao,
        Long roomId,
        String roomNome,
        String formato,
        SessionAudio audio,
        BigDecimal valorBase,
        int lugares,
        long ocupados
) {
}
