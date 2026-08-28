package com.app.cineticket.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO (
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 72) String senha
) {}
