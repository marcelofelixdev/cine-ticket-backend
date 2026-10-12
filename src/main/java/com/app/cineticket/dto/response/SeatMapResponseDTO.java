package com.app.cineticket.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Mapa de assentos de uma sessão: base da tela de escolha do lugar. */
public record SeatMapResponseDTO(
        Long sessionId,
        LocalDateTime horarioInicio,
        String salaNome,
        int totalAssentos,
        int assentosLivres,
        List<SeatStatusDTO> assentos
) {
}
