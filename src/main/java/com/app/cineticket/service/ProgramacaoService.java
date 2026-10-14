package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.dto.request.MovieOfferDTO;
import com.app.cineticket.dto.response.AdminMovieDTO;
import com.app.cineticket.dto.response.CinemaProgramacaoDTO;
import com.app.cineticket.dto.response.ProgramacaoItemDTO;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Programação por cinema: o admin envia filmes para os cinemas e o gerente confirma no seu.
 * Só filmes ATIVOS aparecem no catálogo público.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramacaoService {

    private final CinemaMovieRepository cinemaMovieRepository;
    private final CinemaRepository cinemaRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    /** Envia o filme para os cinemas; cinemas que já têm o filme ficam como estão. */
    @Transactional
    public List<CinemaProgramacaoDTO> offer(Long movieId, MovieOfferDTO request, User admin) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Filme não encontrado. ID: " + movieId));
        for (Long cinemaId : new LinkedHashSet<>(request.cinemaIds())) {
            Cinema cinema = cinemaRepository.findById(cinemaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + cinemaId));
            if (cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaId, movieId).isPresent()) {
                continue;
            }
            CinemaMovie entry = new CinemaMovie();
            entry.setCinema(cinema);
            entry.setMovie(movie);
            entry.setStatus(ProgramacaoStatus.AGUARDANDO_GERENTE);
            entry.setOfferedBy(userRepository.getReferenceById(admin.getId()));
            cinemaMovieRepository.save(entry);
            log.info("[PROGRAMACAO] Filme {} enviado ao cinema {} por {}.", movieId, cinemaId, admin.getEmail());
        }
        return cinemasOf(movieId);
    }

    /** O gerente confirma um filme enviado pelo admin; confirmar de novo não muda nada. */
    @Transactional
    public ProgramacaoItemDTO confirm(Long movieId, User manager) {
        Long cinemaId = cinemaIdOf(manager);
        CinemaMovie entry = cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaId, movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Este filme não foi enviado para o seu cinema."));
        if (entry.getStatus() != ProgramacaoStatus.ATIVO) {
            activate(entry, manager);
            log.info("[PROGRAMACAO] Filme {} confirmado no cinema {} por {}.", movieId, cinemaId, manager.getEmail());
        }
        return toItem(entry);
    }

    /** Programação do cinema do gerente: ativos e aguardando confirmação. */
    @Transactional(readOnly = true)
    public List<ProgramacaoItemDTO> listForManager(User manager) {
        return cinemaMovieRepository.findByCinemaWithMovie(cinemaIdOf(manager)).stream()
                .map(ProgramacaoService::toItem)
                .toList();
    }

    /** Catálogo inteiro (inclusive filmes fora de cartaz) com os cinemas de cada filme. */
    @Transactional(readOnly = true)
    public Page<AdminMovieDTO> listForAdmin(Pageable pageable) {
        Page<Movie> movies = movieRepository.findAll(pageable);
        Map<Long, List<CinemaProgramacaoDTO>> byMovie = cinemaMovieRepository
                .findByMovieIdsWithCinema(movies.map(Movie::getId).getContent()).stream()
                .collect(Collectors.groupingBy(entry -> entry.getMovie().getId(),
                        Collectors.mapping(ProgramacaoService::toCinemaDto, Collectors.toList())));
        return movies.map(movie -> new AdminMovieDTO(
                movie.getId(),
                movie.getTitulo(),
                movie.getTmdbId(),
                movie.getPosterUrl(),
                movie.getDuracaoEmMinutos(),
                movie.getClassificacao(),
                byMovie.getOrDefault(movie.getId(), List.of())));
    }

    /**
     * Deixa o filme ativo no cinema (cria a entrada se preciso). Usado na aprovação de pedido:
     * o gerente já pediu o filme, então não precisa confirmar de novo.
     */
    @Transactional
    public void activateFor(Cinema cinema, Movie movie, User requester) {
        CinemaMovie entry = cinemaMovieRepository.findByCinemaIdAndMovieId(cinema.getId(), movie.getId())
                .orElseGet(() -> {
                    CinemaMovie created = new CinemaMovie();
                    created.setCinema(cinema);
                    created.setMovie(movie);
                    return created;
                });
        if (entry.getStatus() != ProgramacaoStatus.ATIVO) {
            activate(entry, requester);
        }
    }

    private void activate(CinemaMovie entry, User confirmedBy) {
        entry.setStatus(ProgramacaoStatus.ATIVO);
        entry.setConfirmedBy(confirmedBy == null ? null : userRepository.getReferenceById(confirmedBy.getId()));
        entry.setConfirmedAt(LocalDateTime.now(AppTimeZone.ZONE));
        cinemaMovieRepository.save(entry);
    }

    private List<CinemaProgramacaoDTO> cinemasOf(Long movieId) {
        return cinemaMovieRepository.findByMovieIdsWithCinema(List.of(movieId)).stream()
                .map(ProgramacaoService::toCinemaDto)
                .toList();
    }

    static Long cinemaIdOf(User manager) {
        if (manager.getCinema() == null) {
            throw new ForbiddenException("Gerente sem cinema vinculado.");
        }
        return manager.getCinema().getId();
    }

    private static CinemaProgramacaoDTO toCinemaDto(CinemaMovie entry) {
        return new CinemaProgramacaoDTO(entry.getCinema().getId(), entry.getCinema().getNome(), entry.getStatus());
    }

    private static ProgramacaoItemDTO toItem(CinemaMovie entry) {
        Movie movie = entry.getMovie();
        return new ProgramacaoItemDTO(
                movie.getId(),
                movie.getTitulo(),
                movie.getPosterUrl(),
                movie.getClassificacao(),
                movie.getDuracaoEmMinutos(),
                entry.getStatus(),
                entry.getConfirmedAt());
    }
}
