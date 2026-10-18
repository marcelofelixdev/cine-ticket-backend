-- Pedido da compra online: vários lugares reservados juntos (todos ou nenhum) e pagos numa
-- cobrança só. Cada ingresso do pedido aponta para ele.
--   PENDING: lugares reservados, aguardando o pagamento até expires_at.
--   PAID: pagamento confirmado; os ingressos foram aprovados.
--   EXPIRED: o prazo passou sem pagamento; os lugares foram liberados.
CREATE TABLE tb_order (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES tb_user (id) ON DELETE CASCADE,
    session_id        BIGINT        NOT NULL REFERENCES tb_session (id) ON DELETE CASCADE,
    status            VARCHAR(20)   NOT NULL,
    total             NUMERIC(10, 2) NOT NULL,
    expires_at        TIMESTAMP     NOT NULL,
    payment_intent_id VARCHAR(255),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED'))
);

CREATE UNIQUE INDEX idx_order_payment_intent ON tb_order (payment_intent_id) WHERE payment_intent_id IS NOT NULL;
CREATE INDEX idx_order_pending_expiration ON tb_order (expires_at) WHERE status = 'PENDING';

ALTER TABLE tb_ticket ADD COLUMN order_id BIGINT REFERENCES tb_order (id) ON DELETE SET NULL;
CREATE INDEX idx_ticket_order ON tb_ticket (order_id);

-- Os ingressos de um pedido dividem o mesmo PaymentIntent: o índice deixa de ser único.
DROP INDEX idx_ticket_payment_intent;
CREATE INDEX idx_ticket_payment_intent ON tb_ticket (payment_intent_id) WHERE payment_intent_id IS NOT NULL;

-- Estorno passa a ser por ingresso (parte do PaymentIntent do pedido): único por ingresso e pagamento.
DROP INDEX idx_refund_payment_intent;
CREATE UNIQUE INDEX idx_refund_ticket_payment_intent
    ON tb_refund (ticket_id, payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;
