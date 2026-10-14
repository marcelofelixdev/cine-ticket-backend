package com.app.cineticket.service;

import com.app.cineticket.client.TmdbClient;
import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.client.TmdbSearchResponseDTO;
import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.request.MovieUpdateDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import com.app.cineticket.dto.response.TmdbCandidateDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.exception.TmdbUnavailableException;
import com.app.cineticket.mapper.MovieMapper;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    static final int MAX_CANDIDATES = 10;

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final MovieMapper movieMapper;

    /**
     * Com {@code tmdbId}: busca a ficha completa no TMDB (falha do TMDB = 502).
     * Sem {@code tmdbId}: comportamento antigo, procura pelo título e usa o primeiro resultado,
     * sem impedir o cadastro se o TMDB falhar.
     */
    @Transactional
    public MovieResponseDTO create(MovieRequestDTO request) {
        Movie movie = new Movie();
        movie.setTitulo(trimToNull(request.titulo()));
        movie.setDuracaoEmMinutos(request.duracaoEmMinutos());

        if (request.tmdbId() != null) {
            String tmdbId = String.valueOf(request.tmdbId());
            if (movieRepository.existsByTmdbId(tmdbId)) {
                throw new ConflictException("Este filme já está no catálogo.");
            }
            movie.setTmdbId(tmdbId);
            applyDetails(movie, fetchDetails(request.tmdbId()));
        } else {
            enrichByTitleBestEffort(movie);
        }

        applyManualFields(movie, request.sinopse(), request.classificacao(), request.dataEstreia());
        requireTitleAndDuration(movie);
        return movieMapper.toResponseDTO(movieRepository.save(movie));
    }

    /** Cadastra só pelo TMDB (aprovação de pedido do gerente). Quem chama garante que o filme ainda não existe. */
    @Transactional
    Movie createFromTmdb(long tmdbId) {
        Movie movie = new Movie();
        movie.setTmdbId(String.valueOf(tmdbId));
        applyDetails(movie, fetchDetails(tmdbId));
        requireTitleAndDuration(movie);
        return movieRepository.save(movie);
    }

    private static void requireTitleAndDuration(Movie movie) {
        if (movie.getTitulo() == null || movie.getDuracaoEmMinutos() == null) {
            throw new BusinessException("O TMDB não informou título ou duração deste filme; envie titulo e duracaoEmMinutos.");
        }
    }

    /** Candidatos do TMDB para o painel, marcando os que já estão no catálogo. */
    @Transactional(readOnly = true)
    public List<TmdbCandidateDTO> searchTmdb(String query) {
        TmdbSearchResponseDTO response;
        try {
            response = tmdbClient.searchMovieByName(query.trim());
        } catch (RestClientException e) {
            throw new TmdbUnavailableException("Não foi possível pesquisar no TMDB agora. Tente de novo.", e);
        }
        if (response == null || response.results() == null) {
            return List.of();
        }
        List<TmdbSearchResponseDTO.TmdbMovieDTO> results = response.results().stream()
                .filter(result -> result.id() != null)
                .limit(MAX_CANDIDATES)
                .toList();
        Map<String, Long> inCatalog = movieRepository
                .findByTmdbIdIn(results.stream().map(result -> String.valueOf(result.id())).toList()).stream()
                .collect(Collectors.toMap(Movie::getTmdbId, Movie::getId, (a, b) -> a));
        return results.stream()
                .map(result -> new TmdbCandidateDTO(
                        result.id(),
                        result.title(),
                        result.original_title(),
                        year(result.release_date()),
                        result.poster_path() == null ? null : MovieTmdbEnricher.POSTER_BASE + result.poster_path(),
                        result.overview(),
                        inCatalog.get(String.valueOf(result.id()))))
                .toList();
    }

    /** Busca de novo a ficha no TMDB (elenco, classificação, trailer...). Título e duração não mudam. */
    @Transactional
    public MovieResponseDTO syncFromTmdb(Long id) {
        Movie movie = findMovie(id);
        if (movie.getTmdbId() == null) {
            throw new BusinessException("Este filme não está vinculado ao TMDB.");
        }
        MovieTmdbEnricher.apply(movie, fetchDetails(Long.parseLong(movie.getTmdbId())), now());
        return movieMapper.toResponseDTO(movieRepository.save(movie));
    }

    /** Catálogo público: só filmes em cartaz em algum cinema (ou no cinema informado). */
    @Transactional(readOnly = true)
    public Page<MovieResponseDTO> findAll(Pageable pageable, Long cinemaId) {
        return movieRepository.findAll(MovieSpecifications.emCartaz(cinemaId), pageable)
                .map(movieMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public MovieResponseDTO findById(Long id) {
        return movieMapper.toResponseDTO(findMovie(id));
    }

    /** Correção manual: só os campos enviados mudam. */
    @Transactional
    public MovieResponseDTO update(Long id, MovieUpdateDTO request) {
        Movie movie = findMovie(id);
        if (request.titulo() != null && !request.titulo().isBlank()) {
            movie.setTitulo(request.titulo().trim());
        }
        if (request.duracaoEmMinutos() != null) {
            movie.setDuracaoEmMinutos(request.duracaoEmMinutos());
        }
        applyManualFields(movie, request.sinopse(), request.classificacao(), request.dataEstreia());
        return movieMapper.toResponseDTO(movieRepository.save(movie));
    }

    @Transactional
    public void delete(Long id) {
        if (!movieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Filme não encontrado. ID: " + id);
        }

        movieRepository.deleteById(id);
    }

    private Movie findMovie(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filme não encontrado. ID: " + id));
    }

    TmdbMovieDetailsDTO fetchDetails(long tmdbId) {
        try {
            TmdbMovieDetailsDTO details = tmdbClient.getMovieDetails(tmdbId);
            if (details == null) {
                throw new TmdbUnavailableException("O TMDB não devolveu os dados do filme.", null);
            }
            return details;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Filme não encontrado no TMDB. ID: " + tmdbId);
        } catch (RestClientException e) {
            throw new TmdbUnavailableException("Não foi possível buscar o filme no TMDB agora. Tente de novo.", e);
        }
    }

    /** Título e duração do TMDB só entram se o cadastro não trouxe os seus. */
    private void applyDetails(Movie movie, TmdbMovieDetailsDTO details) {
        if (movie.getTitulo() == null && details.title() != null && !details.title().isBlank()) {
            String title = details.title().trim();
            movie.setTitulo(title.length() > 150 ? title.substring(0, 150) : title);
        }
        if (movie.getDuracaoEmMinutos() == null && details.runtime() != null && details.runtime() > 0) {
            movie.setDuracaoEmMinutos(details.runtime());
        }
        MovieTmdbEnricher.apply(movie, details, now());
    }

    private void enrichByTitleBestEffort(Movie movie) {
        try {
            var response = tmdbClient.searchMovieByName(movie.getTitulo());
            if (response == null || response.results() == null || response.results().isEmpty()) {
                return;
            }
            Long tmdbId = response.results().get(0).id();
            if (tmdbId == null || movieRepository.existsByTmdbId(String.valueOf(tmdbId))) {
                return;
            }
            movie.setTmdbId(String.valueOf(tmdbId));
            applyDetails(movie, tmdbClient.getMovieDetails(tmdbId));
        } catch (Exception e) {
            log.warn("Falha ao buscar dados no TMDB: {}", e.getMessage());
        }
    }

    private static void applyManualFields(Movie movie, String sinopse, String classificacao,
                                          java.time.LocalDate dataEstreia) {
        if (sinopse != null && !sinopse.isBlank()) {
            movie.setSinopse(sinopse.trim());
        }
        if (classificacao != null) {
            movie.setClassificacao(classificacao);
        }
        if (dataEstreia != null) {
            movie.setDataEstreia(dataEstreia);
        }
    }

    private static Integer year(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(AppTimeZone.ZONE);
    }
}
