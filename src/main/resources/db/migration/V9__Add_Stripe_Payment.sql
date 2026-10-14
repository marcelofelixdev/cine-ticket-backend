-- Pagamento pelo Stripe (modo de teste).
-- payment_intent_id: o PaymentIntent que pagou o ingresso (usado também no reembolso).
-- tb_processed_webhook_event: deduplicação dos webhooks; o Stripe pode reenviar o mesmo evento.

ALTER TABLE tb_ticket ADD COLUMN payment_intent_id VARCHAR(255);

CREATE UNIQUE INDEX idx_ticket_payment_intent
    ON tb_ticket (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

CREATE TABLE tb_processed_webhook_event (
    event_id     VARCHAR(255) PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    received_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
