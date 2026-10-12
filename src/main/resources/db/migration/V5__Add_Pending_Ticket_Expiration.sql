-- Reserva com prazo: um ingresso PENDING expira se o pagamento não for confirmado a tempo,
-- liberando o assento (o índice idx_ticket_no_overbooking só considera PENDING e APPROVED).

ALTER TABLE tb_ticket ADD COLUMN expires_at TIMESTAMP;

-- Ingressos pendentes já existentes ganham o prazo padrão de 10 minutos a partir da criação.
UPDATE tb_ticket
   SET expires_at = COALESCE(created_at, CURRENT_TIMESTAMP) + INTERVAL '10 minutes'
 WHERE status_pagamento = 'PENDING';

-- Novo status EXPIRED.
ALTER TABLE tb_ticket DROP CONSTRAINT IF EXISTS tb_ticket_status_pagamento_check;
ALTER TABLE tb_ticket
    ADD CONSTRAINT tb_ticket_status_pagamento_check
    CHECK (status_pagamento IN ('PENDING', 'APPROVED', 'CANCELLED', 'EMERGENCY_CANCELLED', 'EXPIRED'));

-- Consulta do job de expiração.
CREATE INDEX idx_ticket_pending_expires_at ON tb_ticket (expires_at) WHERE status_pagamento = 'PENDING';
