-- Transactional outbox: o evento é gravado na mesma transação da regra de negócio
-- (compra, cancelamento) e só depois publicado no RabbitMQ pelo OutboxRelay.
-- Se o broker estiver fora do ar, o evento fica aqui até ser publicado: nada se perde
-- entre o commit no banco e o envio para a fila.

CREATE TABLE tb_outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    exchange        VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);

-- O relay só lê o que falta publicar, em ordem de criação.
CREATE INDEX idx_outbox_unpublished ON tb_outbox_event (id) WHERE published_at IS NULL;
-- Limpeza diária dos já publicados.
CREATE INDEX idx_outbox_published_at ON tb_outbox_event (published_at) WHERE published_at IS NOT NULL;
