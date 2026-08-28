package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;

public record RoomRequestDTO (
    @NotBlank(message = "Nome da sala é obrigatório")
    @Size(max = 100)
    String nome,

    @NotNull(message = "A capacidade é obrigatória")
    @Positive(message = "A capacidade deve ser maior que zero")
    @Max(value = 1000, message = "A capacidade máxima permitida é 1000")
    Integer capacidade,

    @NotNull(message = "O ID do cinema é obrigatório")
    @Positive
    Long cinemaId
) {}
