package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.dto.request.CinemaRequestDTO;
import com.app.cineticket.dto.response.CinemaResponseDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.CinemaMapper;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final SessionRepository sessionRepository;
    private final CinemaMapper cinemaMapper;

    @Transactional
    public CinemaResponseDTO create(CinemaRequestDTO requestDTO) {

        if (cinemaRepository.existsByCnpj(requestDTO.cnpj())) {
            throw new ConflictException("Já existe um cinema cadastrado com esse CNPJ.");
        }

        Cinema cinema = cinemaMapper.toEntity(requestDTO);
        Cinema savedCinema = cinemaRepository.save(cinema);

        return cinemaMapper.toResponseDTO(savedCinema);
    }

    /** Cinemas (público); com cidade, só os daquela cidade (sem diferenciar maiúsculas). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CinemaResponseDTO> findAll(
            org.springframework.data.domain.Pageable pageable, String cidade) {
        var page = cidade == null || cidade.isBlank()
                ? cinemaRepository.findAll(pageable)
                : cinemaRepository.findByCidadeIgnoreCase(cidade.trim(), pageable);
        return page.map(cinemaMapper::toResponseDTO);
    }

    /** Cidades com pelo menos um cinema, em ordem alfabética. */
    @Transactional(readOnly = true)
    public java.util.List<com.app.cineticket.dto.response.CidadeDTO> cidades() {
        return cinemaRepository.findCidades();
    }

    @Transactional(readOnly = true)
    public CinemaResponseDTO findById(Long id) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + id));

        return cinemaMapper.toResponseDTO(cinema);
    }

    @Transactional
    public CinemaResponseDTO update(Long id, CinemaRequestDTO requestDTO) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + id));

        cinema.setCnpj(requestDTO.cnpj());
        cinema.setNome(requestDTO.nome());
        cinema.setEndereco(requestDTO.endereco());
        cinema.setCidade(requestDTO.cidade().trim());
        cinema.setUf(requestDTO.uf());

        return cinemaMapper.toResponseDTO(cinemaRepository.save(cinema));
    }

    /**
     * Exclui o cinema junto com as salas e os assentos dele.
     * <p>
     * Só é permitido quando nenhuma sala do cinema tem sessões: sem sessão não existe
     * ingresso, então nenhum histórico de venda é apagado (mesma regra da exclusão de sala).
     */
    @Transactional
    public void delete(Long id) {
        if (!cinemaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cinema não encontrado. ID: " + id);
        }

        if (sessionRepository.existsByRoomCinemaId(id)) {
            throw new ConflictException("O cinema possui salas com sessões cadastradas e não pode ser excluído.");
        }

        seatRepository.deleteByCinemaId(id);
        roomRepository.deleteByCinemaId(id);
        cinemaRepository.deleteById(id);
    }
}
