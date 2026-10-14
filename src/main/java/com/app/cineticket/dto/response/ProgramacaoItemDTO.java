package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.ProgramacaoStatus;

import java.time.LocalDateTime;

/** Um filme na programação do cinema do gerente. */
public record ProgramacaoItemDTO(
        Long movieId,
        String titulo,
        String posterUrl,
        String classificacao,
        Integer duracaoEmMinutos,
        ProgramacaoStatus status,
        LocalDateTime confirmadoEm
) {
}
