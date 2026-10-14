package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado da venda no caixa, com o QR de cada ingresso para imprimir. Não diz se o e-mail
 * informado tem conta: o caixa não deve conseguir descobrir quem é cliente cadastrado.
 */
public record BoxOfficeSaleResponseDTO(
        Long sessionId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        FormaPagamento formaPagamento,
        BigDecimal total,
        List<Ingresso> ingressos
) {

    public record Ingresso(Long ticketId, String assento, TicketType tipo, BigDecimal valor, String qr) {
    }
}
