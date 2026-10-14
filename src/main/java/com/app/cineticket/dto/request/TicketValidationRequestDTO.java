package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Conteúdo lido do QR Code na entrada da sala. */
public record TicketValidationRequestDTO(
        @NotBlank(message = "O conteúdo do QR Code é obrigatório")
        @Size(max = 100, message = "QR Code inválido")
        String qr
) {}
