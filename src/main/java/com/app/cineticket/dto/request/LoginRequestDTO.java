package com.app.cineticket.dto.request;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO (
        @NotBlank String email,
        @NotBlank String senha
) {}
