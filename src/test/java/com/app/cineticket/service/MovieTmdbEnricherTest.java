package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.CastMember;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.CountryReleases;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.Credits;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.CrewMember;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.Named;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.Release;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.ReleaseDates;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.Video;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO.Videos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovieTmdbEnricherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Test
    void copiaAFichaCompleta() {
        Movie movie = new Movie();

        MovieTmdbEnricher.apply(movie, fullDetails(), NOW);

        assertEquals("Dune: Part Two", movie.getTituloOriginal());
        assertEquals("Sinopse em português.", movie.getSinopse());
        assertEquals(MovieTmdbEnricher.POSTER_BASE + "/poster.jpg", movie.getPosterUrl());
        assertEquals(MovieTmdbEnricher.BACKDROP_BASE + "/fundo.jpg", movie.getBackdropUrl());
        assertEquals("Denis Villeneuve", movie.getDiretor());
        assertEquals("Legendary Pictures, Warner Bros. Pictures, Villeneuve Films", movie.getProdutora());
        assertEquals("Ficção científica, Aventura", movie.getGeneros());
        assertEquals("14", movie.getClassificacao());
        assertEquals(LocalDate.of(2024, 2, 29), movie.getDataEstreia());
        assertEquals("trailerPT", movie.getTrailerYoutubeKey());
        assertEquals(NOW, movie.getTmdbSincronizadoEm());
    }

    @Test
    void elencoTemOsCincoPrimeirosNaOrdemDeDestaque() {
        Movie movie = new Movie();

        MovieTmdbEnricher.apply(movie, fullDetails(), NOW);

        assertEquals(MovieTmdbEnricher.MAX_CAST, movie.getElenco().size());
        assertEquals("Timothée Chalamet", movie.getElenco().get(0).getNome());
        assertEquals("Paul Atreides", movie.getElenco().get(0).getPersonagem());
        assertEquals("Zendaya", movie.getElenco().get(1).getNome());
    }

    @Test
    void campoAusenteNoTmdbNaoApagaOQueJaExiste() {
        Movie movie = new Movie();
        movie.setClassificacao("16");
        movie.setDiretor("Diretor cadastrado");
        movie.setSinopse("Sinopse antiga");

        MovieTmdbEnricher.apply(movie, new TmdbMovieDetailsDTO(1L, "Filme", null, null, 100), NOW);

        assertEquals("16", movie.getClassificacao());
        assertEquals("Diretor cadastrado", movie.getDiretor());
        assertEquals("Sinopse antiga", movie.getSinopse());
        assertEquals(NOW, movie.getTmdbSincronizadoEm());
    }

    @Test
    void semEstreiaNoBrasilUsaADataGeralEIgnoraClassificacaoInvalida() {
        var details = new TmdbMovieDetailsDTO(1L, "Filme", null, null, null, null, 100, "2014-11-05",
                null, null, null,
                new ReleaseDates(List.of(
                        new CountryReleases("US", List.of(new Release("PG-13", 3, "2014-11-07T00:00:00.000Z"))),
                        new CountryReleases("BR", List.of(new Release("", 4, "2015-03-01T00:00:00.000Z"),
                                new Release("ER", 4, "2015-03-01T00:00:00.000Z"))))),
                null);
        Movie movie = new Movie();

        MovieTmdbEnricher.apply(movie, details, NOW);

        assertEquals(LocalDate.of(2014, 11, 5), movie.getDataEstreia());
        assertNull(movie.getClassificacao());
    }

    private static TmdbMovieDetailsDTO fullDetails() {
        List<CastMember> cast = List.of(
                new CastMember("Rebecca Ferguson", "Jessica", 2),
                new CastMember("Timothée Chalamet", "Paul Atreides", 0),
                new CastMember("Zendaya", "Chani", 1),
                new CastMember("Javier Bardem", "Stilgar", 3),
                new CastMember("Josh Brolin", "Gurney Halleck", 4),
                new CastMember("Austin Butler", "Feyd-Rautha", 5));
        List<CrewMember> crew = List.of(
                new CrewMember("Hans Zimmer", "Original Music Composer"),
                new CrewMember("Denis Villeneuve", "Director"),
                new CrewMember("Denis Villeneuve", "Screenplay"));
        List<Named> companies = List.of(new Named("Legendary Pictures"), new Named("Warner Bros. Pictures"),
                new Named("Villeneuve Films"), new Named("Outra"));
        ReleaseDates releases = new ReleaseDates(List.of(
                new CountryReleases("BR", List.of(
                        new Release("", 1, "2024-02-15T00:00:00.000Z"),
                        new Release("14", 3, "2024-02-29T00:00:00.000Z")))));
        Videos videos = new Videos(List.of(
                new Video("teaserEN", "YouTube", "Teaser", "en", true),
                new Video("trailerEN", "YouTube", "Trailer", "en", true),
                new Video("trailerPT", "YouTube", "Trailer", "pt", false),
                new Video("vimeo", "Vimeo", "Trailer", "pt", true)));
        return new TmdbMovieDetailsDTO(693134L, "Duna: Parte Dois", "Dune: Part Two", "Sinopse em português.",
                "/poster.jpg", "/fundo.jpg", 167, "2024-02-27",
                List.of(new Named("Ficção científica"), new Named("Aventura")), companies,
                new Credits(cast, crew), releases, videos);
    }
}
