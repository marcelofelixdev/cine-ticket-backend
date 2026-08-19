package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RoomRequestDTO (
    @NotBlank(message = "Nome da sala é obrigatório")
    String nome,

    @NotNull(message = "A capacidade é obrigatória")
    @Positive(message = "A capacidade deve ser maior que zero")
    Integer capacidade,

    @NotNull(message = "O ID do cinema é obrigatório")
    Long cinemaId
) {}