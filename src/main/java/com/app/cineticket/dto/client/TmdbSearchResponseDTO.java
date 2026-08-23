package com.app.cineticket.dto.client;

import java.util.List;

public record TmdbSearchResponseDTO(List<TmdbMovieDTO> results) {
    public record TmdbMovieDTO(
            Long id,
            String title,
            String overview,
            String poster_path
    ) {}
}