-- Foto do ator ou atriz (TMDB, tamanho w185). Nula para quem não tem foto e para filmes
-- cadastrados antes: o botão "TMDB" do admin (POST /movies/{id}/tmdb-sync) preenche.
ALTER TABLE tb_movie_elenco ADD COLUMN foto_url VARCHAR(255);
