package com.app.cineticket.dto.response;

/** Resultado da busca no TMDB para o painel escolher o filme certo. */
public record TmdbCandidateDTO(
        Long tmdbId,
        String titulo,
        String tituloOriginal,
        Integer ano,
        String posterUrl,
        String sinopse,
        /** ID do filme no nosso catálogo, se já estiver cadastrado (null se não). */
        Long filmeId
) {
}
