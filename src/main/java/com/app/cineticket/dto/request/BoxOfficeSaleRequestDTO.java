package com.app.cineticket.dto.request;

import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Venda no caixa: lugares de uma sessão, pagos na hora. */
public record BoxOfficeSaleRequestDTO(
        @NotNull @Positive Long sessionId,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull Item> itens,
        @NotNull FormaPagamento formaPagamento,
        /** Opcional. Se for de uma conta, os ingressos aparecem em "Meus ingressos" dela. */
        @Email @Size(max = 150) String clienteEmail
) {

    public record Item(
            @NotNull @Positive Long seatId,
            @NotNull TicketType ticketType,
            /** Número da carteira de estudante (só para MEIA_ESTUDANTE). */
            @Size(max = 30) String documentoMeia
    ) {
    }
}
