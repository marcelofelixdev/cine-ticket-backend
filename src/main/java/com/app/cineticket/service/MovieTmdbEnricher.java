package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.MovieCastMember;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copia a ficha do TMDB para o filme. Só sobrescreve com valores presentes: um campo que o
 * TMDB não informa (ex.: classificação brasileira de um filme antigo) não apaga o que já existe.
 * Título e duração ficam com quem chama, porque o cadastro manual tem prioridade sobre eles.
 */
public final class MovieTmdbEnricher {

    public static final String POSTER_BASE = "https://image.tmdb.org/t/p/w500";
    public static final String BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280";

    static final int MAX_CAST = 5;
    private static final int MAX_COMPANIES = 3;
    private static final Set<String> BR_RATINGS = Set.of("L", "10", "12", "14", "16", "18");
    private static final int THEATRICAL_RELEASE = 3;

    private MovieTmdbEnricher() {
    }

    public static void apply(Movie movie, TmdbMovieDetailsDTO details, LocalDateTime now) {
        if (details == null) {
            return;
        }
        setIfPresent(truncate(details.original_title(), 255), movie::setTituloOriginal);
        setIfPresent(details.overview(), movie::setSinopse);
        setIfPresent(posterUrl(details.poster_path()), movie::setPosterUrl);
        if (hasText(details.backdrop_path())) {
            movie.setBackdropUrl(BACKDROP_BASE + details.backdrop_path());
        }
        setIfPresent(truncate(directors(details), 255), movie::setDiretor);
        setIfPresent(truncate(names(details.production_companies(), MAX_COMPANIES), 255), movie::setProdutora);
        setIfPresent(truncate(names(details.genres(), Integer.MAX_VALUE), 255), movie::setGeneros);
        brazilianRating(details).ifPresent(movie::setClassificacao);
        releaseDate(details).ifPresent(movie::setDataEstreia);
        trailerKey(details).ifPresent(movie::setTrailerYoutubeKey);

        List<MovieCastMember> cast = cast(details);
        if (!cast.isEmpty()) {
            movie.getElenco().clear();
            movie.getElenco().addAll(cast);
        }
        movie.setTmdbSincronizadoEm(now);
    }

    /** Título em português pronto para o catálogo (até 150 caracteres), ou null. */
    public static String title(TmdbMovieDetailsDTO details) {
        return hasText(details.title()) ? truncate(details.title().trim(), 150) : null;
    }

    /** Duração em minutos, ou null se o TMDB não informar. */
    public static Integer runtime(TmdbMovieDetailsDTO details) {
        return details.runtime() != null && details.runtime() > 0 ? details.runtime() : null;
    }

    /** URL do pôster (w500) a partir do poster_path do TMDB, ou null. */
    public static String posterUrl(String posterPath) {
        return hasText(posterPath) ? POSTER_BASE + posterPath : null;
    }

    static String directors(TmdbMovieDetailsDTO details) {
        if (details.credits() == null || details.credits().crew() == null) {
            return null;
        }
        String joined = details.credits().crew().stream()
                .filter(member -> "Director".equals(member.job()) && hasText(member.name()))
                .map(TmdbMovieDetailsDTO.CrewMember::name)
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    static List<MovieCastMember> cast(TmdbMovieDetailsDTO details) {
        if (details.credits() == null || details.credits().cast() == null) {
            return List.of();
        }
        return details.credits().cast().stream()
                .filter(member -> hasText(member.name()))
                .sorted(Comparator.comparing(member -> member.order() == null ? Integer.MAX_VALUE : member.order()))
                .limit(MAX_CAST)
                .map(member -> new MovieCastMember(truncate(member.name(), 150),
                        hasText(member.character()) ? truncate(member.character(), 150) : null))
                .toList();
    }

    /** Primeira classificação válida do Brasil (L, 10, 12, 14, 16, 18). */
    static Optional<String> brazilianRating(TmdbMovieDetailsDTO details) {
        return brazilianReleases(details).stream()
                .map(TmdbMovieDetailsDTO.Release::certification)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(BR_RATINGS::contains)
                .findFirst();
    }

    /** Estreia nos cinemas do Brasil; sem ela, a data de lançamento geral. */
    static Optional<LocalDate> releaseDate(TmdbMovieDetailsDTO details) {
        Optional<LocalDate> brazil = brazilianReleases(details).stream()
                .filter(release -> release.type() != null && release.type() == THEATRICAL_RELEASE)
                .map(release -> parseDate(release.release_date()))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
        return brazil.isPresent() ? brazil : parseDate(details.release_date());
    }

    /** Trailer do YouTube: dublado/legendado em português primeiro, depois o oficial. */
    static Optional<String> trailerKey(TmdbMovieDetailsDTO details) {
        if (details.videos() == null || details.videos().results() == null) {
            return Optional.empty();
        }
        return details.videos().results().stream()
                .filter(video -> "YouTube".equals(video.site()) && "Trailer".equals(video.type()) && hasText(video.key()))
                .filter(video -> video.key().length() <= 32)
                .min(Comparator
                        .comparing((TmdbMovieDetailsDTO.Video video) -> !"pt".equals(video.iso_639_1()))
                        .thenComparing(video -> !Boolean.TRUE.equals(video.official())))
                .map(TmdbMovieDetailsDTO.Video::key);
    }

    private static List<TmdbMovieDetailsDTO.Release> brazilianReleases(TmdbMovieDetailsDTO details) {
        if (details.release_dates() == null || details.release_dates().results() == null) {
            return List.of();
        }
        return details.release_dates().results().stream()
                .filter(country -> "BR".equals(country.iso_3166_1()) && country.release_dates() != null)
                .flatMap(country -> country.release_dates().stream())
                .toList();
    }

    private static Optional<LocalDate> parseDate(String value) {
        if (!hasText(value) || value.length() < 10) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.substring(0, 10)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String names(List<TmdbMovieDetailsDTO.Named> items, int max) {
        if (items == null) {
            return null;
        }
        String joined = items.stream()
                .map(TmdbMovieDetailsDTO.Named::name)
                .filter(MovieTmdbEnricher::hasText)
                .limit(max)
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (hasText(value)) {
            setter.accept(value);
        }
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
