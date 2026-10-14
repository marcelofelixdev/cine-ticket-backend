package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.ProgramacaoStatus;

/** Situação de um filme em um cinema. */
public record CinemaProgramacaoDTO(Long cinemaId, String cinemaNome, ProgramacaoStatus status) {
}
