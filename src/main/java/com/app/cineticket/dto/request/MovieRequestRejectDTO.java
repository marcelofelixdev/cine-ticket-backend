package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MovieRequestRejectDTO(@NotBlank @Size(max = 500) String motivo) {
}
