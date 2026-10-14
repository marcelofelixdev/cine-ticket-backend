-- Layout da sala: quantas cadeiras por fila e onde ficam os corredores.
-- corredores: números das cadeiras depois das quais há um corredor (ex.: {3,9}).
-- As salas existentes mantêm 10 cadeiras por fila (como os assentos já foram gerados) e ficam sem corredores.

ALTER TABLE tb_room
    ADD COLUMN cadeiras_por_fila INTEGER   NOT NULL DEFAULT 10,
    ADD COLUMN corredores        INTEGER[] NOT NULL DEFAULT '{}';

ALTER TABLE tb_room
    ADD CONSTRAINT chk_room_cadeiras_por_fila CHECK (cadeiras_por_fila BETWEEN 1 AND 30);
