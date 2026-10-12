-- Proteção contra força bruta por conta: após N senhas erradas seguidas,
-- o login fica bloqueado até locked_until.
ALTER TABLE tb_user ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tb_user ADD COLUMN locked_until TIMESTAMP;
