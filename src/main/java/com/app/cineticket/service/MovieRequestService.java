package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.MovieRequest;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.request.MovieRequestCreateDTO;
import com.app.cineticket.dto.request.MovieRequestRejectDTO;
import com.app.cineticket.dto.response.MovieRequestResponseDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieRequestRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Pedido de filme: o gerente escolhe um filme do TMDB para o próprio cinema e o admin
 * aprova (o filme entra no catálogo e fica ativo no cinema) ou recusa com motivo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRequestService {

    private final MovieRequestRepository movieRequestRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaMovieRepository cinemaMovieRepository;
    private final UserRepository userRepository;
    private final MovieService movieService;
    private final ProgramacaoService programacaoService;

    @Transactional
    public MovieRequestResponseDTO create(MovieRequestCreateDTO request, User manager) {
        Long cinemaId = ProgramacaoService.cinemaIdOf(manager);
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ForbiddenException("Gerente sem cinema vinculado."));
        long tmdbId = request.tmdbId();

        Optional<Movie> inCatalog = movieRepository.findByTmdbId(String.valueOf(tmdbId));
        if (inCatalog.isPresent()) {
            rejectIfAlreadyInCinema(cinemaId, inCatalog.get().getId());
        }
        if (movieRequestRepository.existsByCinemaIdAndTmdbIdAndStatus(cinemaId, tmdbId, MovieRequestStatus.AGUARDANDO_ADMIN)) {
            throw new ConflictException("Já existe um pedido deste filme aguardando o admin.");
        }

        MovieRequest movieRequest = new MovieRequest();
        movieRequest.setTmdbId(tmdbId);
        movieRequest.setCinema(cinema);
        movieRequest.setRequestedBy(userRepository.getReferenceById(manager.getId()));
        movieRequest.setStatus(MovieRequestStatus.AGUARDANDO_ADMIN);
        if (inCatalog.isPresent()) {
            Movie movie = inCatalog.get();
            movieRequest.setTitulo(movie.getTitulo());
            movieRequest.setPosterUrl(movie.getPosterUrl());
            movieRequest.setAno(movie.getDataEstreia() == null ? null : movie.getDataEstreia().getYear());
        } else {
            TmdbMovieDetailsDTO details = movieService.fetchDetails(tmdbId);
            String title = details.title() == null || details.title().isBlank() ? "Filme TMDB " + tmdbId : details.title().trim();
            movieRequest.setTitulo(title.length() > 150 ? title.substring(0, 150) : title);
            movieRequest.setPosterUrl(details.poster_path() == null ? null : MovieTmdbEnricher.POSTER_BASE + details.poster_path());
            movieRequest.setAno(MovieTmdbEnricher.releaseDate(details).map(java.time.LocalDate::getYear).orElse(null));
        }
        MovieRequest saved = movieRequestRepository.save(movieRequest);
        log.info("[PEDIDO] {} pediu o filme TMDB {} para o cinema {}.", manager.getEmail(), tmdbId, cinemaId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MovieRequestResponseDTO> listForManager(User manager) {
        return movieRequestRepository.findByCinema(ProgramacaoService.cinemaIdOf(manager)).stream()
                .map(MovieRequestService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovieRequestResponseDTO> listForAdmin(MovieRequestStatus status) {
        List<MovieRequest> requests = status == null
                ? movieRequestRepository.findAllWithCinema()
                : movieRequestRepository.findByStatusWithCinema(status);
        return requests.stream().map(MovieRequestService::toResponse).toList();
    }

    /** Cadastra o filme (ou reaproveita o do catálogo) e deixa ativo no cinema do pedido. */
    @Transactional
    public MovieRequestResponseDTO approve(Long requestId, User admin) {
        MovieRequest movieRequest = pendingForDecision(requestId);
        Movie movie = movieRepository.findByTmdbId(String.valueOf(movieRequest.getTmdbId()))
                .orElseGet(() -> movieService.createFromTmdb(movieRequest.getTmdbId()));

        programacaoService.activateFor(movieRequest.getCinema(), movie, movieRequest.getRequestedBy());

        movieRequest.setMovie(movie);
        decide(movieRequest, MovieRequestStatus.APROVADO, admin);
        log.info("[PEDIDO] Pedido {} aprovado por {}: filme {} ativo no cinema {}.",
                requestId, admin.getEmail(), movie.getId(), movieRequest.getCinema().getId());
        return toResponse(movieRequest);
    }

    @Transactional
    public MovieRequestResponseDTO reject(Long requestId, MovieRequestRejectDTO request, User admin) {
        MovieRequest movieRequest = pendingForDecision(requestId);
        movieRequest.setMotivoRecusa(request.motivo().trim());
        decide(movieRequest, MovieRequestStatus.RECUSADO, admin);
        log.info("[PEDIDO] Pedido {} recusado por {}.", requestId, admin.getEmail());
        return toResponse(movieRequest);
    }

    private MovieRequest pendingForDecision(Long requestId) {
        MovieRequest movieRequest = movieRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado. ID: " + requestId));
        if (movieRequest.getStatus() != MovieRequestStatus.AGUARDANDO_ADMIN) {
            throw new ConflictException("Este pedido já foi decidido (" + movieRequest.getStatus() + ").");
        }
        return movieRequest;
    }

    private void decide(MovieRequest movieRequest, MovieRequestStatus status, User admin) {
        movieRequest.setStatus(status);
        movieRequest.setDecidedBy(userRepository.getReferenceById(admin.getId()));
        movieRequest.setDecidedAt(java.time.LocalDateTime.now(AppTimeZone.ZONE));
        movieRequestRepository.save(movieRequest);
    }

    private void rejectIfAlreadyInCinema(Long cinemaId, Long movieId) {
        Optional<CinemaMovie> entry = cinemaMovieRepository.findByCinemaIdAndMovieId(cinemaId, movieId);
        if (entry.isEmpty()) {
            return;
        }
        if (entry.get().getStatus() == ProgramacaoStatus.ATIVO) {
            throw new ConflictException("Este filme já está em cartaz no seu cinema.");
        }
        throw new ConflictException("O admin já enviou este filme para o seu cinema: confirme na programação.");
    }

    private static MovieRequestResponseDTO toResponse(MovieRequest request) {
        return new MovieRequestResponseDTO(
                request.getId(),
                request.getTmdbId(),
                request.getTitulo(),
                request.getAno(),
                request.getPosterUrl(),
                request.getCinema().getId(),
                request.getCinema().getNome(),
                request.getRequestedBy() == null ? null : request.getRequestedBy().getEmail(),
                request.getStatus(),
                request.getMotivoRecusa(),
                request.getMovie() == null ? null : request.getMovie().getId(),
                request.getCreatedAt(),
                request.getDecidedAt());
    }
}
