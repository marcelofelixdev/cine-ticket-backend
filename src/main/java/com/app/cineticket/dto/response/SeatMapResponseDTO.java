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
        /** Para desenhar a sala: cadeiras por fila e os corredores (depois de qual cadeira). */
        int cadeirasPorFila,
        List<Integer> corredores,
        /** Meias que ainda cabem na cota da sessão. */
        int meiasRestantes,
        List<SeatStatusDTO> assentos
) {
}
