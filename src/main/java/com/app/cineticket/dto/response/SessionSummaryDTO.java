package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Resumo de uma sessão para o gerente: ocupação, meias e receita. */
public record SessionSummaryDTO(
        Long sessionId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        boolean ativa,
        int lugares,
        /** Ingressos aprovados (online e balcão). */
        long vendidos,
        /** Reservas online aguardando pagamento (ocupam o lugar). */
        long reservados,
        long livres,
        long meias,
        /** Ingressos já validados na entrada. */
        long entradas,
        /** Soma dos ingressos aprovados. */
        BigDecimal receitaTotal,
        BigDecimal receitaOnline,
        BigDecimal receitaBalcao,
        /** Receita do balcão por forma de pagamento. */
        Map<FormaPagamento, BigDecimal> receitaBalcaoPorForma
) {
}
