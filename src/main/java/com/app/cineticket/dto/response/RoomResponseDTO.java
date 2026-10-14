package com.app.cineticket.dto.response;

public record RoomResponseDTO (
        Long id,
        String nome,
        Integer capacidade,
        Long cinemaId,
        String cinemaNome,
        Integer cadeirasPorFila,
        java.util.List<Integer> corredores
) {}
