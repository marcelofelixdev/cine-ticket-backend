package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;

public record MovieRequestDTO(
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150)
        String titulo,
        
        @NotNull(message = "A duração é obrigatória")
        @Positive
        @Max(1440)
        Integer duracaoEmMinutos
) {}
