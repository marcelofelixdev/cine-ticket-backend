package com.app.cineticket.domain.enums;

/** Situação de um pedido da compra online. */
public enum OrderStatus {
    /** Lugares reservados, aguardando o pagamento dentro do prazo. */
    PENDING,
    /** Pagamento confirmado; os ingressos foram aprovados. */
    PAID,
    /** O prazo passou sem pagamento; os lugares foram liberados. */
    EXPIRED
}
