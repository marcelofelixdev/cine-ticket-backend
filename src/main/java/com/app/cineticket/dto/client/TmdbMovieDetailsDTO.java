package com.app.cineticket.dto.client;

/** Resposta de GET /movie/{id} do TMDB (só os campos usados). */
public record TmdbMovieDetailsDTO(
        Long id,
        String title,
        String overview,
        String poster_path,
        Integer runtime
) {
}
