package com.app.cineticket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponseDTO (
        Long id,
        LocalDateTime horarioInicio,
        BigDecimal valorBase,
        String movieTitulo,
        String roomNome,
        String cinemaNome
) {}
