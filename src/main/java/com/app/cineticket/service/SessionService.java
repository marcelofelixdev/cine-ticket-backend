package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.SessionMapper;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final TicketRepository ticketRepository;
    private final SessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SessionResponseDTO create(SessionRequestDTO requestDTO) {
        Movie movie = movieRepository.findById(requestDTO.movieId())
                .orElseThrow(() -> new BusinessException("Filme não encontrado."));

        Room room = roomRepository.findById(requestDTO.roomId())
                .orElseThrow(() -> new BusinessException("Sala não encontrada"));

        Session session = sessionMapper.toEntity(requestDTO);

        List<Session> sessoesNaSala = sessionRepository.findByRoomId(requestDTO.roomId());

        LocalDateTime novoInicio = requestDTO.horarioInicio();
        LocalDateTime novoFim = novoInicio.plusMinutes(movie.getDuracaoEmMinutos() + 30);

        for (Session sessaoExistente : sessoesNaSala) {
            LocalDateTime existenteInicio = sessaoExistente.getHorarioInicio();
            LocalDateTime existenteFim = existenteInicio.plusMinutes(sessaoExistente.getMovie().getDuracaoEmMinutos() + 30);

            if (novoInicio.isBefore(existenteFim) && novoFim.isAfter(existenteInicio)) {
                throw new BusinessException("Conflito de horário! A sala já estará ocupada neste período.");
            }
        }

        session.setMovie(movie);
        session.setRoom(room);

        Session savedSession = sessionRepository.save(session);
        return sessionMapper.toResponseDTO(savedSession);
    }

    @Transactional
    public void deleteEmergency(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Sessão não encontrada"));

        session.setAtivo(false);
        sessionRepository.save(session);

        List<Ticket> ingressosDeSessao = ticketRepository.findBySessionId(sessionId);

        for (Ticket ticket : ingressosDeSessao) {
            ticket.setStatus(TicketStatus.EMERGENCY_CANCELLED);
            eventPublisher.publishEvent(new com.app.cineticket.dto.request.RefundEventDTO(
                    ticket.getId(),
                    ticket.getValorPago()
            ));
        }

        ticketRepository.saveAll(ingressosDeSessao);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SessionResponseDTO> findAll(org.springframework.data.domain.Pageable pageable) {
        return sessionRepository.findByAtivoTrue(pageable)
                .map(sessionMapper::toResponseDTO);
    }
}
