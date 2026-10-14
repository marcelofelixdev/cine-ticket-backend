package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Correção manual pelo admin: só os campos enviados mudam. */
public record MovieUpdateDTO(
        @Size(min = 1, max = 150)
        String titulo,

        @Positive
        @Max(1440)
        Integer duracaoEmMinutos,

        @Size(max = 5000)
        String sinopse,

        @Pattern(regexp = "L|10|12|14|16|18", message = "Classificação deve ser L, 10, 12, 14, 16 ou 18")
        String classificacao,

        LocalDate dataEstreia
) {
}
