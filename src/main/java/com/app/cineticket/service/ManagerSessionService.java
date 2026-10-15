package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.ManagerSessionDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.RoomMapper;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sessões do cinema do gerente: as mesmas regras de {@link SessionService} (conflito de
 * horário, filme em cartaz, cancelamento com estorno), limitadas ao cinema dele.
 */
@Service
@RequiredArgsConstructor
public class ManagerSessionService {


    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final RoomRepository roomRepository;
    private final SessionOccupancyService occupancyService;
    private final BoxOfficeService boxOfficeService;
    private final RoomMapper roomMapper;

    @Transactional
    public SessionResponseDTO create(SessionRequestDTO request, Long cinemaId) {
        Room room = roomRepository.findWithCinemaById(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada."));
        CinemaScope.requireWithin(cinemaId, room.getCinema().getId(), "Esta sala é de outro cinema.");
        return sessionService.create(request);
    }

    @Transactional
    public void cancel(Long sessionId, Long cinemaId) {
        Session session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
        CinemaScope.requireWithin(cinemaId, session.getRoom().getCinema().getId(), "Esta sessão é de outro cinema.");
        sessionService.deleteEmergency(sessionId);
    }

    /**
     * Sessões ativas do dia (padrão: hoje), com lugares, ocupação e meias, em uma consulta de
     * ocupação para o dia todo. {@code withRevenue}: a receita só vai para a lista do gerente.
     */
    @Transactional(readOnly = true)
    public List<ManagerSessionDTO> listOfDay(LocalDate date, Long cinemaId, boolean withRevenue) {
        LocalDate day = date == null ? LocalDate.now(AppTimeZone.ZONE) : date;
        List<Session> sessions = sessionRepository.findActiveByCinemaBetween(cinemaId,
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        Map<Long, SessionOccupancy> occupancy =
                occupancyService.bySession(sessions.stream().map(Session::getId).toList());
        return sessions.stream()
                .map(session -> toDto(session, occupancy.getOrDefault(session.getId(), SessionOccupancy.EMPTY),
                        withRevenue))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponseDTO> rooms(Long cinemaId) {
        return roomRepository.findByCinemaIdOrderByNome(cinemaId).stream()
                .map(roomMapper::toResponseDTO)
                .toList();
    }

    private ManagerSessionDTO toDto(Session session, SessionOccupancy occupancy, boolean withRevenue) {
        int duration = session.getMovie().getDuracaoEmMinutos();
        return new ManagerSessionDTO(
                session.getId(),
                session.getHorarioInicio(),
                session.getHorarioInicio().plusMinutes(duration),
                session.getMovie().getId(),
                session.getMovie().getTitulo(),
                session.getMovie().getClassificacao(),
                session.getRoom().getId(),
                session.getRoom().getNome(),
                session.getFormato(),
                session.getAudio(),
                session.getValorBase(),
                session.getRoom().getCapacidade(),
                occupancy.ocupados(),
                occupancy.meias(),
                withRevenue ? occupancy.receita() : null,
                HalfPricePolicy.priceFor(session.getValorBase(), TicketType.MEIA_ESTUDANTE),
                boxOfficeService.salesOpen(session));
    }
}
