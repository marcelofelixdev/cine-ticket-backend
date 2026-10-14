package com.app.cineticket.dto.client;

import java.util.List;

/**
 * Resposta de GET /movie/{id} do TMDB com append_to_response=credits,release_dates,videos
 * (só os campos usados). Os nomes seguem o JSON do TMDB.
 */
public record TmdbMovieDetailsDTO(
        Long id,
        String title,
        String original_title,
        String overview,
        String poster_path,
        String backdrop_path,
        Integer runtime,
        String release_date,
        List<Named> genres,
        List<Named> production_companies,
        Credits credits,
        ReleaseDates release_dates,
        Videos videos
) {

    /** Atalho para os testes e o plano B: só os campos básicos. */
    public TmdbMovieDetailsDTO(Long id, String title, String overview, String poster_path, Integer runtime) {
        this(id, title, null, overview, poster_path, null, runtime, null, null, null, null, null, null);
    }

    public record Named(String name) {
    }

    public record Credits(List<CastMember> cast, List<CrewMember> crew) {
    }

    public record CastMember(String name, String character, Integer order) {
    }

    public record CrewMember(String name, String job) {
    }

    public record ReleaseDates(List<CountryReleases> results) {
    }

    /** Lançamentos de um país; type 3 = cinema. */
    public record CountryReleases(String iso_3166_1, List<Release> release_dates) {
    }

    public record Release(String certification, Integer type, String release_date) {
    }

    public record Videos(List<Video> results) {
    }

    public record Video(String key, String site, String type, String iso_639_1, Boolean official) {
    }
}
