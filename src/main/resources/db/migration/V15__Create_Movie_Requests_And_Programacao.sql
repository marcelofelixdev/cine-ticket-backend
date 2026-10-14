-- Programação por cinema e pedidos de filme.
--
-- tb_cinema_movie: qual filme está em qual cinema.
--   AGUARDANDO_GERENTE: o admin enviou e o gerente ainda não confirmou.
--   ATIVO: em cartaz no cinema (aparece no site e pode ter sessões).
-- tb_movie_request: o gerente pede um filme do TMDB e o admin aprova ou recusa.

CREATE TABLE tb_cinema_movie (
    id           BIGSERIAL PRIMARY KEY,
    cinema_id    BIGINT      NOT NULL REFERENCES tb_cinema (id) ON DELETE CASCADE,
    movie_id     BIGINT      NOT NULL REFERENCES tb_movie (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    offered_by   BIGINT      REFERENCES tb_user (id) ON DELETE SET NULL,
    confirmed_by BIGINT      REFERENCES tb_user (id) ON DELETE SET NULL,
    confirmed_at TIMESTAMP,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT uk_cinema_movie UNIQUE (cinema_id, movie_id),
    CONSTRAINT chk_cinema_movie_status CHECK (status IN ('AGUARDANDO_GERENTE', 'ATIVO'))
);

CREATE INDEX idx_cinema_movie_movie ON tb_cinema_movie (movie_id, status);

CREATE TABLE tb_movie_request (
    id            BIGSERIAL PRIMARY KEY,
    tmdb_id       BIGINT       NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    ano           INT,
    poster_url    VARCHAR(500),
    cinema_id     BIGINT       NOT NULL REFERENCES tb_cinema (id) ON DELETE CASCADE,
    requested_by  BIGINT       REFERENCES tb_user (id) ON DELETE SET NULL,
    status        VARCHAR(20)  NOT NULL,
    motivo_recusa VARCHAR(500),
    decided_by    BIGINT       REFERENCES tb_user (id) ON DELETE SET NULL,
    decided_at    TIMESTAMP,
    movie_id      BIGINT       REFERENCES tb_movie (id) ON DELETE SET NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT chk_movie_request_status CHECK (status IN ('AGUARDANDO_ADMIN', 'APROVADO', 'RECUSADO'))
);

-- Um pedido aguardando por filme e cinema.
CREATE UNIQUE INDEX uk_movie_request_pending
    ON tb_movie_request (cinema_id, tmdb_id)
    WHERE status = 'AGUARDANDO_ADMIN';

CREATE INDEX idx_movie_request_status ON tb_movie_request (status, created_at);

-- Dados existentes: o filme fica ativo nos cinemas onde já tem sessão.
INSERT INTO tb_cinema_movie (cinema_id, movie_id, status, confirmed_at)
SELECT DISTINCT r.cinema_id, s.movie_id, 'ATIVO', CURRENT_TIMESTAMP
FROM tb_session s
JOIN tb_room r ON r.id = s.room_id;
