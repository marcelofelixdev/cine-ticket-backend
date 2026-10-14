package com.app.cineticket.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Cadastro de filme. O caminho normal é escolher um resultado da busca no TMDB e mandar só o
 * {@code tmdbId}: título, duração e a ficha completa vêm de lá. Sem {@code tmdbId}, título e
 * duração são obrigatórios. Os demais campos, se enviados, substituem o que veio do TMDB.
 */
public record MovieRequestDTO(
        @Size(max = 150)
        String titulo,

        @Positive
        @Max(1440)
        Integer duracaoEmMinutos,

        @Positive
        Long tmdbId,

        @Size(max = 5000)
        String sinopse,

        @Pattern(regexp = "L|10|12|14|16|18", message = "Classificação deve ser L, 10, 12, 14, 16 ou 18")
        String classificacao,

        LocalDate dataEstreia
) {

    @JsonIgnore
    @AssertTrue(message = "Informe o tmdbId ou o título e a duração")
    public boolean isIdentificado() {
        return tmdbId != null || (titulo != null && !titulo.isBlank() && duracaoEmMinutos != null);
    }
}
