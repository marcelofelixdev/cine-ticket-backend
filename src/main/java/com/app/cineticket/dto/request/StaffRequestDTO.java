package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Gerente cadastra um funcionário: o papel e o cinema vêm do próprio gerente, não do pedido. */
public record StaffRequestDTO(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 12, max = 72) String senha
) {}
