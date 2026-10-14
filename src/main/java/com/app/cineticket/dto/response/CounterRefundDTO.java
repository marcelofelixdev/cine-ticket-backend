package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Devolução de venda de balcão (lista do caixa e confirmação). */
public record CounterRefundDTO(
        Long ticketId,
        String filmeTitulo,
        String salaNome,
        LocalDateTime horarioSessao,
        String assento,
        /** Como o cliente pagou: em geral a devolução é na mesma forma. */
        FormaPagamento formaPagamento,
        BigDecimal valor,
        String clienteEmail,
        RefundStatus status,
        LocalDateTime devolvidoEm
) {
}
