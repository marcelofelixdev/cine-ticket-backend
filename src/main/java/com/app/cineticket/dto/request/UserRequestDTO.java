package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserRequestDTO (
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 12, max = 72) String senha,
        /** Opcional; necessária para meia-entrada de idoso. */
        @jakarta.validation.constraints.Past(message = "A data de nascimento deve estar no passado")
        java.time.LocalDate dataNascimento
) {}
