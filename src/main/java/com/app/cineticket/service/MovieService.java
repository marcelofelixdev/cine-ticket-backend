package com.app.cineticket.service;

import com.app.cineticket.client.TmdbClient;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.MovieMapper;
import com.app.cineticket.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final MovieMapper movieMapper;

    @Transactional
    public MovieResponseDTO create(MovieRequestDTO requestDTO) {
        Movie movie = movieMapper.toEntity(requestDTO);

        try {
            var tmdbResponse = tmdbClient.searchMovieByName(movie.getTitulo());

            if (tmdbResponse.results() != null && !tmdbResponse.results().isEmpty()) {
                var filmeGringo = tmdbResponse.results().get(0);

                movie.setSinopse(filmeGringo.overview());

                if (filmeGringo.poster_path() != null) {
                    movie.setPosterUrl("https://image.tmdb.org/t/p/w500" + filmeGringo.poster_path());
                }
            }
        } catch (Exception e) {
            System.out.println("Falha ao buscar dados no TMDB: " + e.getMessage());
        }

        Movie savedMovie = movieRepository.save(movie);
        return movieMapper.toResponseDTO(savedMovie);
    }

    @Transactional(readOnly = true)
    public List<MovieResponseDTO> findAll() {
        return movieRepository.findAll().stream()
                .map(movieMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MovieResponseDTO findById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Filme não encontrado. ID: " + id));
        return movieMapper.toResponseDTO(movie);
    }

    @Transactional
    public MovieResponseDTO update(Long id, MovieRequestDTO requestDTO) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Filme não encontrado. ID:" + id));

        movie.setTitulo(requestDTO.titulo());
        movie.setDuracaoEmMinutos(requestDTO.duracaoEmMinutos());

        return movieMapper.toResponseDTO(movieRepository.save(movie));
    }

    @Transactional
    public void delete(Long id) {
        if (!movieRepository.existsById(id)) {
            throw new BusinessException("Filme não encontrado. ID: " + id);
        }

        movieRepository.deleteById(id);
    }
}