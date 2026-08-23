package com.app.cineticket.controller;

import com.app.cineticket.dto.request.CinemaRequestDTO;
import com.app.cineticket.dto.response.CinemaResponseDTO;
import com.app.cineticket.service.CinemaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @PostMapping
    public ResponseEntity<CinemaResponseDTO> create(@RequestBody @Valid CinemaRequestDTO requestDTO) {
        CinemaResponseDTO response = cinemaService.create(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<CinemaResponseDTO>> findAll(
            @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "nome") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(cinemaService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CinemaResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cinemaService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CinemaResponseDTO> update(
            @PathVariable Long id, @RequestBody @Valid CinemaRequestDTO requestDTO) {
        return ResponseEntity.ok(cinemaService.update(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cinemaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
