package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.RoomMapper;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatRepository seatRepository;
    private final SessionRepository sessionRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponseDTO create(RoomRequestDTO requestDTO) {
        Cinema cinema = cinemaRepository.findById(requestDTO.cinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + requestDTO.cinemaId()));

        Room room = roomMapper.toEntity(requestDTO);
        room.setCinema(cinema);

        Room savedRoom = roomRepository.save(room);

        gerarAssentoParaSala(savedRoom);

        return roomMapper.toResponseDTO(savedRoom);
    }

    @Transactional
    public RoomResponseDTO update(Long id, RoomRequestDTO requestDTO) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada. ID: " + id));

        if (!java.util.Objects.equals(room.getCapacidade(), requestDTO.capacidade())) {
            throw new BusinessException("A capacidade da sala não pode ser alterada após a criação");
        }
        if (!java.util.Objects.equals(room.getCinema().getId(), requestDTO.cinemaId())) {
            throw new BusinessException("Uma sala não pode ser transferida para outro cinema");
        }

        room.setNome(requestDTO.nome());

        return roomMapper.toResponseDTO(roomRepository.save(room));
    }

    /**
     * Exclui a sala e os assentos gerados para ela.
     * <p>
     * Só é permitido quando a sala não tem sessões: sem sessão não existe ingresso,
     * então nenhum histórico de venda é apagado. O lock na sala impede que uma sessão
     * seja criada nela enquanto a exclusão acontece (a criação de sessão usa o mesmo lock).
     */
    @Transactional
    public void delete(Long id) {
        Room room = roomRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada. ID: " + id));

        if (sessionRepository.existsByRoomId(id)) {
            throw new ConflictException("A sala possui sessões cadastradas e não pode ser excluída.");
        }

        seatRepository.deleteByRoomId(id);
        roomRepository.delete(room);
    }

    private void gerarAssentoParaSala(Room room) {
        int capacidade = room.getCapacidade();
        int maxCadeirasPorFila = 10;

        List<Seat> cadeirasParaSalvar = new ArrayList<>();

        char letraFila = 'A';
        int numeroCadeira = 1;

        for (int i = 0; i < capacidade; i++) {
            Seat seat = new Seat();
            seat.setFila(String.valueOf(letraFila));
            seat.setNumero(numeroCadeira);
            seat.setRoom(room);

            cadeirasParaSalvar.add(seat);

            numeroCadeira++;

            if (numeroCadeira > maxCadeirasPorFila) {
                numeroCadeira = 1;
                letraFila++;
            }
        }

        seatRepository.saveAll(cadeirasParaSalvar);
    }

    @Transactional(readOnly = true)
    public RoomResponseDTO findById(Long id) {
        return roomRepository.findWithCinemaById(id)
                .map(roomMapper::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada. ID: " + id));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RoomResponseDTO> findAll(org.springframework.data.domain.Pageable pageable) {
        return roomRepository.findAll(pageable)
                .map(roomMapper::toResponseDTO);
    }
}
