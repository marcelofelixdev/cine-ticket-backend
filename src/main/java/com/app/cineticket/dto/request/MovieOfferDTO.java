package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin envia um filme do catálogo para os cinemas; cada gerente confirma no seu. */
public record MovieOfferDTO(@NotEmpty @Size(max = 100) List<@NotNull Long> cinemaIds) {
}
