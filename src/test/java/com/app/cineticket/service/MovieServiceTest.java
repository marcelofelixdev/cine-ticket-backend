package com.app.cineticket.service;

import com.app.cineticket.client.TmdbClient;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.client.TmdbSearchResponseDTO;
import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import com.app.cineticket.dto.response.TmdbCandidateDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.exception.TmdbUnavailableException;
import com.app.cineticket.mapper.MovieMapper;
import com.app.cineticket.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock private MovieRepository movieRepository;
    @Mock private TmdbClient tmdbClient;

    private MovieService movieService;

    @BeforeEach
    void setUp() {
        movieService = new MovieService(movieRepository, tmdbClient, Mappers.getMapper(MovieMapper.class));
    }

    @Test
    void cadastroPorTmdbIdUsaTituloEDuracaoDoTmdbEOsCamposManuaisPrevalecem() {
        when(movieRepository.existsByTmdbId("693134")).thenReturn(false);
        when(tmdbClient.getMovieDetails(693134L)).thenReturn(
                new TmdbMovieDetailsDTO(693134L, "Duna: Parte Dois", "Sinopse do TMDB", "/p.jpg", 167));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieResponseDTO response = movieService.create(new MovieRequestDTO(null, null, 693134L, null, "14", null));

        assertEquals("Duna: Parte Dois", response.titulo());
        assertEquals(167, response.duracaoEmMinutos());
        assertEquals("693134", response.tmdbId());
        assertEquals("Sinopse do TMDB", response.sinopse());
        assertEquals("14", response.classificacao());
    }

    @Test
    void filmeJaNoCatalogoRecebe409SemChamarOTmdb() {
        when(movieRepository.existsByTmdbId("693134")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> movieService.create(new MovieRequestDTO(null, null, 693134L, null, null, null)));
        verify(tmdbClient, never()).getMovieDetails(693134L);
    }

    @Test
    void tmdbIdInexistenteRecebe404() {
        when(movieRepository.existsByTmdbId("1")).thenReturn(false);
        when(tmdbClient.getMovieDetails(1L)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null));

        assertThrows(ResourceNotFoundException.class,
                () -> movieService.create(new MovieRequestDTO(null, null, 1L, null, null, null)));
        verify(movieRepository, never()).save(any());
    }

    @Test
    void tmdbForaDoArRecebe502() {
        when(movieRepository.existsByTmdbId("2")).thenReturn(false);
        when(tmdbClient.getMovieDetails(2L)).thenThrow(new ResourceAccessException("timeout"));

        assertThrows(TmdbUnavailableException.class,
                () -> movieService.create(new MovieRequestDTO(null, null, 2L, null, null, null)));
    }

    @Test
    void cadastroManualContinuaFuncionandoSemTmdb() {
        when(tmdbClient.searchMovieByName("Filme Local")).thenThrow(new ResourceAccessException("timeout"));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieResponseDTO response = movieService.create(new MovieRequestDTO("Filme Local", 95, null, null, null, null));

        assertEquals("Filme Local", response.titulo());
        assertNull(response.tmdbId());
    }

    @Test
    void buscaMarcaOsFilmesQueJaEstaoNoCatalogo() {
        when(tmdbClient.searchMovieByName("duna")).thenReturn(new TmdbSearchResponseDTO(List.of(
                new TmdbSearchResponseDTO.TmdbMovieDTO(693134L, "Duna: Parte Dois", "Dune: Part Two", "...", "/p.jpg", "2024-02-27"),
                new TmdbSearchResponseDTO.TmdbMovieDTO(438631L, "Duna", "Dune", "...", null, ""))));
        Movie existing = new Movie();
        existing.setId(7L);
        existing.setTmdbId("693134");
        when(movieRepository.findByTmdbIdIn(List.of("693134", "438631"))).thenReturn(List.of(existing));

        List<TmdbCandidateDTO> candidates = movieService.searchTmdb("duna");

        assertEquals(2, candidates.size());
        assertEquals(7L, candidates.get(0).filmeId());
        assertEquals(2024, candidates.get(0).ano());
        assertEquals(MovieTmdbEnricher.POSTER_BASE + "/p.jpg", candidates.get(0).posterUrl());
        assertNull(candidates.get(1).filmeId());
        assertNull(candidates.get(1).ano());
    }

    @Test
    void sincronizarFilmeSemTmdbIdRecebe422() {
        Movie movie = new Movie();
        movie.setId(3L);
        when(movieRepository.findById(3L)).thenReturn(Optional.of(movie));

        assertThrows(BusinessException.class, () -> movieService.syncFromTmdb(3L));
    }
}
