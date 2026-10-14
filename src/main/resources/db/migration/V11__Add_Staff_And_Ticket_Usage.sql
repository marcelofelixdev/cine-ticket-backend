-- Validação de ingressos na entrada da sala.
-- ROLE_STAFF: funcionário que só valida ingressos (menor privilégio que ROLE_ADMIN).
-- used_at/used_by: quando e por quem o ingresso foi usado; um ingresso só entra uma vez.

INSERT INTO tb_role (nome) VALUES ('ROLE_STAFF') ON CONFLICT DO NOTHING;

ALTER TABLE tb_ticket ADD COLUMN used_at TIMESTAMP;
ALTER TABLE tb_ticket ADD COLUMN used_by BIGINT REFERENCES tb_user (id) ON DELETE SET NULL;
