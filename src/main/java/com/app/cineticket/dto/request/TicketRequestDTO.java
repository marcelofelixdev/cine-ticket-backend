package com.app.cineticket.dto.request;
import jakarta.validation.constraints.NotNull;

public record TicketRequestDTO (
        @NotNull(message = "A Sessão é obrigatória") Long sessionId,
        @NotNull(message = "A Cadeira é obrigatória") Long seatId
){}
