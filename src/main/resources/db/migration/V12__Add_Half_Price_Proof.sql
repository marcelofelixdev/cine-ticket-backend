-- Comprovação de meia-entrada.
-- data_nascimento: confere a meia do idoso (60+ na data da sessão).
-- documento_meia: número da carteira de estudante, conferido na entrada.

ALTER TABLE tb_user ADD COLUMN data_nascimento DATE;
ALTER TABLE tb_ticket ADD COLUMN documento_meia VARCHAR(30);

-- Apoia a contagem da cota legal de meia-entrada por sessão (Lei 12.933/2013).
CREATE INDEX idx_ticket_session_type_status ON tb_ticket (session_id, ticket_type, status_pagamento);
