package com.app.cineticket.dto.response;

public record MovieResponseDTO (
        Long id,
        String titulo,
        String sinopse,
        String tmdbId,
        String posterUrl,
        Integer duracaoEmMinutos
) {}
