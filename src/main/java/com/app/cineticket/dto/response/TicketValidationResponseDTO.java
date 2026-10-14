package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.TicketType;

import java.time.LocalDateTime;

/**
 * Resultado da validação na entrada: o que o funcionário precisa conferir.
 *
 * @param alerta o que conferir pessoalmente (ex.: documento da meia-entrada), ou null
 */
public record TicketValidationResponseDTO(
        Long ticketId,
        String filmeTitulo,
        String cinemaNome,
        String salaNome,
        String cadeiraAssento,
        LocalDateTime horarioSessao,
        TicketType ticketType,
        String alerta,
        LocalDateTime utilizadoEm
) {}
