package com.app.cineticket.dto.request;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TicketRequestDTO (
        @NotNull(message = "A Sessão é obrigatória") @Positive Long sessionId,
        @NotNull(message = "A Cadeira é obrigatória") @Positive Long seatId,
        @NotNull(message = "O Tipo de Ingresso é obrigatório")TicketType ticketType,
        /** Número da carteira de estudante; obrigatório para MEIA_ESTUDANTE. */
        @jakarta.validation.constraints.Size(max = 30, message = "Documento da meia-entrada excede 30 caracteres")
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9./ -]*$",
                message = "Documento da meia-entrada aceita apenas letras, números, ponto, barra e hífen")
        String documentoMeia
        ){}
