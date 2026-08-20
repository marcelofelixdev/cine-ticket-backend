package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.mapper.RoomMapper;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatRepository seatRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponseDTO create(RoomRequestDTO requestDTO) {
        Cinema cinema = cinemaRepository.findById(requestDTO.cinemaId())
                .orElseThrow(() -> new RuntimeException("Cinema não encontrado. ID: " + requestDTO.cinemaId()));

        Room room = roomMapper.toEntity(requestDTO);
        room.setCinema(cinema);

        Room savedRoom = roomRepository.save(room);

        gerarAssentoParaSala(savedRoom);

        return roomMapper.toResponseDTO(savedRoom);
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
    public List<RoomResponseDTO> findAll() {
        return roomRepository.findAll().stream()
                .map(roomMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
}
