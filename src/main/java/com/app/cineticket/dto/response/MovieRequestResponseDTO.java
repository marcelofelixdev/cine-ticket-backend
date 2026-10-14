package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.MovieRequestStatus;

import java.time.LocalDateTime;

public record MovieRequestResponseDTO(
        Long id,
        Long tmdbId,
        String titulo,
        Integer ano,
        String posterUrl,
        Long cinemaId,
        String cinemaNome,
        /** E-mail de quem pediu (null se a conta foi apagada). */
        String solicitadoPor,
        MovieRequestStatus status,
        String motivoRecusa,
        /** Filme do catálogo, depois de aprovado. */
        Long filmeId,
        LocalDateTime criadoEm,
        LocalDateTime decididoEm
) {
}
