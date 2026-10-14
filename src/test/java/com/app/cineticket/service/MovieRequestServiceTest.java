package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.MovieRequest;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.request.MovieRequestCreateDTO;
import com.app.cineticket.dto.response.MovieRequestResponseDTO;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.TmdbUnavailableException;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieRequestRepository;
import com.app.cineticket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pedido de filme que ainda não está no catálogo: o título e o pôster vêm do TMDB (simulado). */
@ExtendWith(MockitoExtension.class)
class MovieRequestServiceTest {

    @Mock private MovieRequestRepository movieRequestRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private CinemaRepository cinemaRepository;
    @Mock private CinemaMovieRepository cinemaMovieRepository;
    @Mock private UserRepository userRepository;
    @Mock private MovieService movieService;
    @Mock private ProgramacaoService programacaoService;

    @InjectMocks private MovieRequestService movieRequestService;

    private User manager;
    private Cinema cinema;

    @BeforeEach
    void setUp() {
        cinema = new Cinema();
        cinema.setId(5L);
        cinema.setNome("Cine Teste");
        manager = new User();
        manager.setId(9L);
        manager.setEmail("gerente@teste.com");
        manager.setCinema(cinema);
    }

    @Test
    void filmeForaDoCatalogoUsaTituloEPosterDoTmdb() {
        when(cinemaRepository.getReferenceById(5L)).thenReturn(cinema);
        when(movieRepository.findByTmdbId("27205")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(9L)).thenReturn(manager);
        when(movieService.fetchDetails(27205L)).thenReturn(new TmdbMovieDetailsDTO(27205L, "A Origem", null, null,
                "/origem.jpg", null, 148, "2010-07-15", null, null, null, null, null));
        when(movieRequestRepository.save(any(MovieRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieRequestResponseDTO response = movieRequestService.create(new MovieRequestCreateDTO(27205L), manager);

        assertEquals("A Origem", response.titulo());
        assertEquals(2010, response.ano());
        assertEquals(MovieTmdbEnricher.POSTER_BASE + "/origem.jpg", response.posterUrl());
        assertEquals(MovieRequestStatus.AGUARDANDO_ADMIN, response.status());
        assertEquals("gerente@teste.com", response.solicitadoPor());
    }

    @Test
    void tmdbForaDoArNaoCriaPedido() {
        when(cinemaRepository.getReferenceById(5L)).thenReturn(cinema);
        when(movieRepository.findByTmdbId("27205")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(9L)).thenReturn(manager);
        when(movieService.fetchDetails(27205L)).thenThrow(new TmdbUnavailableException("fora do ar", null));

        assertThrows(TmdbUnavailableException.class,
                () -> movieRequestService.create(new MovieRequestCreateDTO(27205L), manager));
        verify(movieRequestRepository, never()).save(any());
    }

    @Test
    void gerenteSemCinemaRecebe403() {
        manager.setCinema(null);

        assertThrows(ForbiddenException.class,
                () -> movieRequestService.create(new MovieRequestCreateDTO(27205L), manager));
    }
}
