-- Venda no caixa (balcão).
-- canal: ONLINE (site, pago pelo Stripe) ou BALCAO (caixa do cinema, sem Stripe).
-- forma_pagamento: só no balcão (dinheiro, crédito, débito ou Pix na maquininha).
-- vendido_por: funcionário ou gerente que fez a venda.
-- cliente_email: opcional; se for de uma conta, o ingresso fica nessa conta (user_id).
-- user_id deixa de ser obrigatório: o cliente do balcão pode não ter conta.

ALTER TABLE tb_ticket ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE tb_ticket
    ADD COLUMN canal           VARCHAR(10)  NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN forma_pagamento VARCHAR(10),
    ADD COLUMN vendido_por     BIGINT       REFERENCES tb_user (id) ON DELETE SET NULL,
    ADD COLUMN cliente_email   VARCHAR(150);

ALTER TABLE tb_ticket
    ADD CONSTRAINT chk_ticket_canal CHECK (canal IN ('ONLINE', 'BALCAO')),
    ADD CONSTRAINT chk_ticket_forma_pagamento CHECK (forma_pagamento IN ('DINHEIRO', 'CREDITO', 'DEBITO', 'PIX')),
    -- Online sempre tem dono; balcão sempre tem forma de pagamento.
    ADD CONSTRAINT chk_ticket_online_tem_dono CHECK (canal = 'BALCAO' OR user_id IS NOT NULL),
    ADD CONSTRAINT chk_ticket_balcao_tem_pagamento CHECK (canal = 'ONLINE' OR forma_pagamento IS NOT NULL);
