package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Resultado da venda no caixa, com o QR de cada ingresso para imprimir. */
public record BoxOfficeSaleResponseDTO(
        Long sessionId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        FormaPagamento formaPagamento,
        BigDecimal total,
        /** true se os ingressos foram para a conta do e-mail informado. */
        boolean vinculadoAConta,
        List<Ingresso> ingressos
) {

    public record Ingresso(Long ticketId, String assento, TicketType tipo, BigDecimal valor, String qr) {
    }
}
