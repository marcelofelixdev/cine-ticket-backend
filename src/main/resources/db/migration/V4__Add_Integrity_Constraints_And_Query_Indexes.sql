ALTER TABLE tb_room
    ADD CONSTRAINT ck_room_capacity_positive CHECK (capacidade > 0);

ALTER TABLE tb_movie
    ADD CONSTRAINT ck_movie_duration_positive CHECK (duracao_minutos > 0);

ALTER TABLE tb_session
    ADD CONSTRAINT ck_session_value_positive CHECK (valor_base > 0);

ALTER TABLE tb_seat
    ADD CONSTRAINT ck_seat_number_positive CHECK (numero > 0);

CREATE UNIQUE INDEX idx_user_email_lower ON tb_user (lower(email));
CREATE INDEX idx_ticket_user_id_id ON tb_ticket (user_id, id);
CREATE INDEX idx_ticket_session_id ON tb_ticket (session_id);
CREATE INDEX idx_session_room_start ON tb_session (room_id, horario_inicio);
CREATE INDEX idx_session_active_start ON tb_session (horario_inicio) WHERE ativo = true;
