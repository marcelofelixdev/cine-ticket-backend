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

    /** As filas vão de A a Z. */
    static final int MAX_FILAS = 26;

    @Transactional
    public RoomResponseDTO create(RoomRequestDTO requestDTO) {
        Cinema cinema = cinemaRepository.findById(requestDTO.cinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + requestDTO.cinemaId()));

        Room room = roomMapper.toEntity(requestDTO);
        room.setCinema(cinema);
        int cadeirasPorFila = requestDTO.cadeirasPorFila() == null
                ? Room.CADEIRAS_POR_FILA_PADRAO : requestDTO.cadeirasPorFila();
        int filas = (requestDTO.capacidade() + cadeirasPorFila - 1) / cadeirasPorFila;
        if (filas > MAX_FILAS) {
            throw new BusinessException("Com " + cadeirasPorFila + " cadeiras por fila, a sala teria " + filas
                    + " filas; o máximo é " + MAX_FILAS + " (A a Z). Aumente as cadeiras por fila.");
        }
        room.setCadeirasPorFila(cadeirasPorFila);
        room.setCorredores(corredores(requestDTO.corredores(), cadeirasPorFila));

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

        if (requestDTO.cadeirasPorFila() != null && !requestDTO.cadeirasPorFila().equals(room.getCadeirasPorFila())) {
            throw new BusinessException("As cadeiras por fila não podem ser alteradas após a criação");
        }

        room.setNome(requestDTO.nome());
        if (requestDTO.corredores() != null) {
            room.setCorredores(corredores(requestDTO.corredores(), room.getCadeirasPorFila()));
        }

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

    /** Corredores em ordem, sem repetição, e cada um antes da última cadeira da fila. */
    private static List<Integer> corredores(List<Integer> pedidos, int cadeirasPorFila) {
        if (pedidos == null) {
            return new ArrayList<>();
        }
        List<Integer> ordenados = pedidos.stream().distinct().sorted().collect(Collectors.toCollection(ArrayList::new));
        if (ordenados.size() != pedidos.size()) {
            throw new BusinessException("O mesmo corredor foi informado mais de uma vez.");
        }
        if (!ordenados.isEmpty() && ordenados.get(ordenados.size() - 1) >= cadeirasPorFila) {
            throw new BusinessException("Cada corredor deve ficar depois de uma cadeira entre 1 e "
                    + (cadeirasPorFila - 1) + ".");
        }
        return ordenados;
    }

    private void gerarAssentoParaSala(Room room) {
        int capacidade = room.getCapacidade();
        int maxCadeirasPorFila = room.getCadeirasPorFila();

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
    public org.springframework.data.domain.Page<RoomResponseDTO> findAll(Long cinemaId,
                                                                        org.springframework.data.domain.Pageable pageable) {
        return (cinemaId == null ? roomRepository.findAll(pageable) : roomRepository.findByCinemaId(cinemaId, pageable))
                .map(roomMapper::toResponseDTO);
    }
}
