package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

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
    Long cinemaId,

    /** Opcional (padrão 10). Não muda depois da criação, como a capacidade. */
    @Positive(message = "As cadeiras por fila devem ser maiores que zero")
    @Max(value = 30, message = "No máximo 30 cadeiras por fila")
    Integer cadeirasPorFila,

    /**
     * Opcional. Número de cada cadeira depois da qual há um corredor (ex.: [3, 9]).
     * Na criação, nulo = sem corredores; na edição, nulo = mantém os atuais.
     */
    @Size(max = 10, message = "No máximo 10 corredores")
    List<@NotNull @Positive(message = "O corredor deve ficar depois de uma cadeira válida") Integer> corredores
) {

    /** Sala com o layout padrão: 10 cadeiras por fila, sem corredores. */
    public RoomRequestDTO(String nome, Integer capacidade, Long cinemaId) {
        this(nome, capacidade, cinemaId, null, null);
    }
}
