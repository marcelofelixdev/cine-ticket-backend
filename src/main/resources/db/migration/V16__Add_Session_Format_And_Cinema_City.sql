-- Dados de cinema de shopping.
-- Sessão: formato (2D/3D) e áudio (dublado, legendado ou nacional). As existentes ficam 2D legendado.
-- Cinema: cidade e UF, para o seletor de cidade do site (vazios nos cinemas antigos).

ALTER TABLE tb_session
    ADD COLUMN formato VARCHAR(2)  NOT NULL DEFAULT '2D',
    ADD COLUMN audio   VARCHAR(10) NOT NULL DEFAULT 'LEGENDADO';

ALTER TABLE tb_session
    ADD CONSTRAINT chk_session_formato CHECK (formato IN ('2D', '3D')),
    ADD CONSTRAINT chk_session_audio CHECK (audio IN ('DUBLADO', 'LEGENDADO', 'NACIONAL'));

ALTER TABLE tb_cinema
    ADD COLUMN cidade VARCHAR(100),
    ADD COLUMN uf     VARCHAR(2);

CREATE INDEX idx_cinema_cidade ON tb_cinema (LOWER(cidade));
