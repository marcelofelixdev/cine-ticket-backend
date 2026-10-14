package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Gerente pede um filme do TMDB (o tmdbId vem da busca em /tmdb/search). */
public record MovieRequestCreateDTO(@NotNull @Positive Long tmdbId) {
}
