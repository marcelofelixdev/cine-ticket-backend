package com.app.cineticket.dto.request;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record TicketRequestDTO (
        @NotNull(message = "A Sessão é obrigatória") @Positive Long sessionId,
        @NotNull(message = "A Cadeira é obrigatória") @Positive Long seatId,
        @NotNull(message = "O Tipo de Ingresso é obrigatório")TicketType ticketType,
        @NotBlank(message = "O Token do cartão é obrigatório")
        @Size(max = 512, message = "O Token do cartão excede o tamanho permitido") String cartaoToken
        ){}
