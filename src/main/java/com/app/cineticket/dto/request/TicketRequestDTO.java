package com.app.cineticket.dto.request;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TicketRequestDTO (
        @NotNull(message = "A Sessão é obrigatória") @Positive Long sessionId,
        @NotNull(message = "A Cadeira é obrigatória") @Positive Long seatId,
        @NotNull(message = "O Tipo de Ingresso é obrigatório")TicketType ticketType
        ){}
