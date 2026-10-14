package com.app.cineticket.dto.client;

import java.util.List;

public record TmdbSearchResponseDTO(List<TmdbMovieDTO> results) {
    public record TmdbMovieDTO(
            Long id,
            String title,
            String original_title,
            String overview,
            String poster_path,
            String release_date
    ) {}
}
