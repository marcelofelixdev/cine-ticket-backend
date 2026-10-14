-- Ficha completa do filme, vinda do TMDB (pode ser corrigida pelo admin).
-- classificacao: classificação indicativa brasileira (L, 10, 12, 14, 16, 18).
-- tmdb_sincronizado_em: última vez que os dados foram buscados no TMDB.

ALTER TABLE tb_movie
    ADD COLUMN titulo_original      VARCHAR(255),
    ADD COLUMN backdrop_url         VARCHAR(500),
    ADD COLUMN diretor              VARCHAR(255),
    ADD COLUMN produtora            VARCHAR(255),
    ADD COLUMN generos              VARCHAR(255),
    ADD COLUMN classificacao        VARCHAR(2),
    ADD COLUMN trailer_youtube_key  VARCHAR(32),
    ADD COLUMN data_estreia         DATE,
    ADD COLUMN tmdb_sincronizado_em TIMESTAMP;

ALTER TABLE tb_movie
    ADD CONSTRAINT chk_movie_classificacao CHECK (classificacao IN ('L', '10', '12', '14', '16', '18'));

-- Elenco principal, na ordem de destaque do TMDB.
CREATE TABLE tb_movie_elenco (
    movie_id   BIGINT       NOT NULL REFERENCES tb_movie (id) ON DELETE CASCADE,
    ordem      INT          NOT NULL,
    nome       VARCHAR(150) NOT NULL,
    personagem VARCHAR(150),
    PRIMARY KEY (movie_id, ordem)
);
