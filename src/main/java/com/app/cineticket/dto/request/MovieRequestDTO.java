package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MovieRequestDTO(
        @NotBlank(message = "O título é obrigatório")
        String titulo,
        
        @NotNull(message = "A duração é obrigatória")
        @Positive
        Integer duracaoEmMinutos
) {}
