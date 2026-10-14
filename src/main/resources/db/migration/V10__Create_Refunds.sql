-- Reembolsos. Um registro por pagamento estornado: o mesmo ingresso pode ter dois
-- pagamentos (pagamento duplicado), e cada um é estornado separadamente.
-- O UNIQUE em payment_intent_id é o que impede estorno em dobro quando a mesma
-- mensagem chega mais de uma vez.

CREATE TABLE tb_refund (
    id                 BIGSERIAL PRIMARY KEY,
    ticket_id          BIGINT        NOT NULL REFERENCES tb_ticket (id) ON DELETE CASCADE,
    payment_intent_id  VARCHAR(255),
    amount             NUMERIC(10, 2) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    gateway_refund_id  VARCHAR(255),
    attempts           INT           NOT NULL DEFAULT 0,
    last_error         VARCHAR(500),
    refunded_at        TIMESTAMP,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP,
    CONSTRAINT chk_refund_status CHECK (status IN ('PENDING', 'REFUNDED', 'FAILED', 'NOT_APPLICABLE'))
);

CREATE UNIQUE INDEX idx_refund_payment_intent
    ON tb_refund (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

CREATE INDEX idx_refund_ticket ON tb_refund (ticket_id);
