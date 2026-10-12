package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.SeatAvailability;

public record SeatStatusDTO(
        Long id,
        String fila,
        Integer numero,
        SeatAvailability status
) {
}
