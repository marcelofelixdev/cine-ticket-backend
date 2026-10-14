-- Equipe por cinema.
-- ROLE_MANAGER: gerente de um cinema (programação e equipe do próprio cinema).
-- cinema_id: cinema do gerente ou do funcionário. Fica vazio para clientes e admins (a rede toda).
-- Se o cinema for excluído, a equipe fica sem cinema até ser vinculada de novo.

INSERT INTO tb_role (nome) VALUES ('ROLE_MANAGER') ON CONFLICT DO NOTHING;

ALTER TABLE tb_user ADD COLUMN cinema_id BIGINT REFERENCES tb_cinema (id) ON DELETE SET NULL;
CREATE INDEX idx_user_cinema ON tb_user (cinema_id);

-- Funcionários que já existiam passam a pertencer ao primeiro cinema cadastrado (se houver).
UPDATE tb_user u
SET cinema_id = (SELECT MIN(c.id) FROM tb_cinema c)
WHERE u.cinema_id IS NULL
  AND EXISTS (SELECT 1
              FROM tb_user_role ur
              JOIN tb_role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.nome = 'ROLE_STAFF');
