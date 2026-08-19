package com.app.cineticket.dto.response;

public record CinemaResponseDTO (
    Long id,
    String nome,
    String cnpj,
    String endereco
) {}