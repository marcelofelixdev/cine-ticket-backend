package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ingresso na lista do painel (gerente e admin). */
public record TicketAdminDTO(
        Long id,
        TicketStatus status,
        TicketChannel canal,
        FormaPagamento formaPagamento,
        Long sessionId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        String assento,
        TicketType tipo,
        BigDecimal valorPago,
        /** E-mail da conta do cliente ou o informado no balcão (null se nenhum). */
        String clienteEmail,
        /** Quem vendeu no balcão (null em vendas online). */
        String vendidoPor,
        String reembolso,
        LocalDateTime utilizadoEm,
        LocalDateTime criadoEm
) {
}
