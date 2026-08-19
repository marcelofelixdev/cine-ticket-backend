package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.mapper.SessionMapper;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SessionMapper sessionMapper;

    @Transactional
    public SessionResponseDTO create(SessionRequestDTO requestDTO) {
        Movie movie = movieRepository.findById(requestDTO.movieId())
                .orElseThrow(() -> new RuntimeException("Filme não encontrado."));

        Room room = roomRepository.findById(requestDTO.roomId())
                .orElseThrow(() -> new RuntimeException("Sala não encontrada"));

        Session session = sessionMapper.toEntity(requestDTO);
        session.setMovie(movie);
        session.setRoom(room);

        Session savedSession = sessionRepository.save(session);
        return sessionMapper.toResponseDTO(savedSession);
    }

    @Transactional(readOnly = true)
    public List<SessionResponseDTO> findAll() {
        return sessionRepository.findAll().stream()
                .map(sessionMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
