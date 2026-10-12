-- Refresh tokens rotativos. Só o hash SHA-256 é guardado: um vazamento do banco
-- não permite usar os tokens. Tokens da mesma "família" vêm do mesmo login;
-- reutilizar um token já trocado revoga a família inteira (detecção de roubo).

CREATE TABLE tb_refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES tb_user (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_refresh_token_family ON tb_refresh_token (family_id);
CREATE INDEX idx_refresh_token_user ON tb_refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires_at ON tb_refresh_token (expires_at);
