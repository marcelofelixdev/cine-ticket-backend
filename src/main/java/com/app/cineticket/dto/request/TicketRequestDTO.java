package com.app.cineticket.dto.request;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketRequestDTO (
        @NotNull(message = "A Sessão é obrigatória") Long sessionId,
        @NotNull(message = "A Cadeira é obrigatória") Long seatId,
        @NotNull(message = "O Tipo de Ingresso é obrigatório")TicketType ticketType,
        @NotBlank(message = "O Token do cartão é obrigatório")String cartaoToken
        ){}
