package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.dto.request.CinemaRequestDTO;
import com.app.cineticket.dto.response.CinemaResponseDTO;
import com.app.cineticket.mapper.CinemaMapper;
import com.app.cineticket.repository.CinemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final CinemaMapper cinemaMapper;

    @Transactional
    public CinemaResponseDTO create(CinemaRequestDTO requestDTO) {

        if (cinemaRepository.existsByCnpj(requestDTO.cnpj())) {
            throw new RuntimeException("Já existe um cinema cadastrado com esse CNPJ.");
        }

        Cinema cinema = cinemaMapper.toEntity(requestDTO);
        Cinema savedCinema = cinemaRepository.save(cinema);

        return cinemaMapper.toResponseDTO(savedCinema);
    }

    @Transactional(readOnly = true)
    public List<CinemaResponseDTO> findAll() {
        return cinemaRepository.findAll()
                .stream()
                .map(cinemaMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CinemaResponseDTO findById(Long id) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cinema não encontrado. ID: " + id));

        return cinemaMapper.toResponseDTO(cinema);
    }
}
