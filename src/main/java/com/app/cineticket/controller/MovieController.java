package com.app.cineticket.controller;

import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import com.app.cineticket.service.MovieService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<MovieResponseDTO> create(@RequestBody @Valid MovieRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.create(requestDTO));
    }

    @GetMapping
    public ResponseEntity<List<MovieResponseDTO>> findAll() {
        return ResponseEntity.ok(movieService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.findById(id));
    }
}
