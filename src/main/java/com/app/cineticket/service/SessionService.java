package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.enums.SeatAvailability;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.SessionFilter;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SeatMapResponseDTO;
import com.app.cineticket.dto.response.SeatStatusDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.SessionMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.SessionSpecifications;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final TicketRepository ticketRepository;
    private final SessionMapper sessionMapper;
    private final OutboxService outboxService;
    private final SeatRepository seatRepository;
    private final CinemaMovieRepository cinemaMovieRepository;
    private final CounterRefundService counterRefundService;


    /**
     * Mapa de assentos de uma sessão ativa: cada assento da sala com status LIVRE ou OCUPADO,
     * ordenado por fila e número. Público, para quem ainda não fez login poder escolher o lugar.
     */
    @Transactional(readOnly = true)
    public SeatMapResponseDTO getSeatMap(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .filter(s -> Boolean.TRUE.equals(s.getAtivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));

        Set<Long> occupiedSeatIds = new HashSet<>(
                ticketRepository.findSeatIdsBySessionIdAndStatusIn(sessionId, TicketStatus.OCCUPYING_SEAT));

        List<Seat> seats = seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(session.getRoom().getId());
        List<SeatStatusDTO> seatStatuses = seats.stream()
                .map(seat -> new SeatStatusDTO(seat.getId(), seat.getFila(), seat.getNumero(),
                        occupiedSeatIds.contains(seat.getId()) ? SeatAvailability.OCUPADO : SeatAvailability.LIVRE))
                .toList();

        int freeSeats = (int) seatStatuses.stream().filter(s -> s.status() == SeatAvailability.LIVRE).count();

        return new SeatMapResponseDTO(session.getId(), session.getHorarioInicio(), session.getRoom().getNome(),
                seatStatuses.size(), freeSeats, session.getRoom().getCadeirasPorFila(),
                List.copyOf(session.getRoom().getCorredores()), seatStatuses);
    }

    @Transactional
    public SessionResponseDTO create(SessionRequestDTO requestDTO) {
        Movie movie = movieRepository.findById(requestDTO.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Filme não encontrado."));

        Room room = roomRepository.findByIdWithLock(requestDTO.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada."));

        // Sessão só de filme em cartaz no cinema da sala (programação do item 7.3).
        if (!cinemaMovieRepository.existsByCinemaIdAndMovieIdAndStatus(
                room.getCinema().getId(), movie.getId(), ProgramacaoStatus.ATIVO)) {
            throw new BusinessException("O filme não está em cartaz neste cinema: envie o filme para o cinema "
                    + "e aguarde a confirmação do gerente antes de criar sessões.");
        }

        Session session = sessionMapper.toEntity(requestDTO);

        List<Session> sessoesNaSala = sessionRepository.findByRoomIdAndAtivoTrue(requestDTO.roomId());

        LocalDateTime novoInicio = requestDTO.horarioInicio();
        LocalDateTime novoFim = novoInicio.plusMinutes(movie.getDuracaoEmMinutos() + 30);

        for (Session sessaoExistente : sessoesNaSala) {
            LocalDateTime existenteInicio = sessaoExistente.getHorarioInicio();
            LocalDateTime existenteFim = existenteInicio.plusMinutes(sessaoExistente.getMovie().getDuracaoEmMinutos() + 30);

            if (novoInicio.isBefore(existenteFim) && novoFim.isAfter(existenteInicio)) {
                throw new ConflictException("Conflito de horário: a sala já estará ocupada neste período.");
            }
        }

        session.setMovie(movie);
        session.setRoom(room);

        Session savedSession = sessionRepository.save(session);
        return sessionMapper.toResponseDTO(savedSession);
    }

    @Transactional
    public void deleteEmergency(Long sessionId) {
        Session session = sessionRepository.findByIdWithLock(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));

        if (!Boolean.TRUE.equals(session.getAtivo())) {
            return;
        }

        session.setAtivo(false);
        sessionRepository.save(session);

        List<Ticket> ingressosDeSessao = ticketRepository.findBySessionIdForUpdate(sessionId);

        for (Ticket ticket : ingressosDeSessao) {
            TicketStatus previousStatus = ticket.getStatus();
            if (previousStatus != TicketStatus.PENDING && previousStatus != TicketStatus.APPROVED) {
                continue;
            }
            ticket.setStatus(TicketStatus.EMERGENCY_CANCELLED);
            if (previousStatus == TicketStatus.APPROVED
                    && ticket.getCanal() == com.app.cineticket.domain.enums.TicketChannel.BALCAO) {
                // Venda de balcão: o dinheiro volta no caixa, não pelo gateway.
                counterRefundService.registerPending(ticket);
            } else if (previousStatus == TicketStatus.APPROVED) {
                outboxService.refundRequested(new com.app.cineticket.dto.request.RefundEventDTO(
                        ticket.getId(), ticket.getPaymentIntentId(), ticket.getValorPago()));
            }
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SessionResponseDTO> findAll(
            SessionFilter filter, org.springframework.data.domain.Pageable pageable) {
        List<org.springframework.data.jpa.domain.Specification<Session>> specs = new ArrayList<>();
        specs.add(SessionSpecifications.active());

        if (filter.movieId() != null) {
            specs.add(SessionSpecifications.movieId(filter.movieId()));
        }
        if (filter.cinemaId() != null) {
            specs.add(SessionSpecifications.cinemaId(filter.cinemaId()));
        }
        if (filter.formato() != null) {
            specs.add(SessionSpecifications.formato(filter.formato()));
        }
        if (filter.audio() != null) {
            specs.add(SessionSpecifications.audio(filter.audio()));
        }

        LocalDateTime from = filter.includePast() ? null : LocalDateTime.now();
        if (filter.date() != null) {
            LocalDateTime startOfDay = filter.date().atStartOfDay();
            from = (from == null || startOfDay.isAfter(from)) ? startOfDay : from;
            specs.add(SessionSpecifications.startsBefore(filter.date().plusDays(1).atStartOfDay()));
        }
        if (from != null) {
            specs.add(SessionSpecifications.startsFrom(from));
        }

        return sessionRepository.findAll(org.springframework.data.jpa.domain.Specification.allOf(specs), pageable)
                .map(sessionMapper::toResponseDTO);
    }

    /** Detalhe de uma sessão ativa. Sessão inexistente ou cancelada responde 404. */
    @Transactional(readOnly = true)
    public SessionResponseDTO findById(Long id) {
        return sessionRepository.findWithDetailsById(id)
                .filter(session -> Boolean.TRUE.equals(session.getAtivo()))
                .map(sessionMapper::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
    }
}
