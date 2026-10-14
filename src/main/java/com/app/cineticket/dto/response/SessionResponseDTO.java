package com.app.cineticket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponseDTO (
        Long id,
        LocalDateTime horarioInicio,
        BigDecimal valorBase,
        Long movieId,
        String movieTitulo,
        Long roomId,
        String roomNome,
        Long cinemaId,
        String cinemaNome,
        String formato,
        com.app.cineticket.domain.enums.SessionAudio audio
) {}
