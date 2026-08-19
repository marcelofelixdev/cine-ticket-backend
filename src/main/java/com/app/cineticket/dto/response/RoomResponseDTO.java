package com.app.cineticket.dto.response;

public record RoomResponseDTO (
        Long id,
        String nome,
        Integer capacidade,
        String cinemaNome
) {}
