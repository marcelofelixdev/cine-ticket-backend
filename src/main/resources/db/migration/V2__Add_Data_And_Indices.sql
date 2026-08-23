INSERT INTO tb_role (nome) VALUES ('ROLE_USER') ON CONFLICT DO NOTHING;
INSERT INTO tb_role (nome) VALUES ('ROLE_ADMIN') ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX idx_ticket_no_overbooking 
ON tb_ticket(session_id, seat_id) 
WHERE status_pagamento IN ('APPROVED', 'PENDING');
