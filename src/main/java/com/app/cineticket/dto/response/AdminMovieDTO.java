package com.app.cineticket.dto.response;

import java.util.List;

/** Filme do catálogo com os cinemas onde está (painel do admin). */
public record AdminMovieDTO(
        Long id,
        String titulo,
        String tmdbId,
        String posterUrl,
        Integer duracaoEmMinutos,
        String classificacao,
        List<CinemaProgramacaoDTO> cinemas
) {
}
