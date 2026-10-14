-- Devolução no caixa: quem devolveu o dinheiro (funcionário ou gerente).
-- Vazio nos estornos pelo gateway (Stripe) e nas devoluções ainda pendentes.
ALTER TABLE tb_refund ADD COLUMN refunded_by BIGINT REFERENCES tb_user (id) ON DELETE SET NULL;

CREATE INDEX idx_refund_counter_pending ON tb_refund (status) WHERE gateway_refund_id = 'BALCAO';
