package com.app.cineticket.controller;

import com.app.cineticket.dto.request.SessionFilter;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SeatMapResponseDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<SessionResponseDTO> create(@RequestBody @Valid SessionRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(requestDTO));
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<SessionResponseDTO>> findAll(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date,
            @RequestParam(defaultValue = "false") boolean includePast,
            @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "horarioInicio") org.springframework.data.domain.Pageable pageable) {
        SessionFilter filter = new SessionFilter(movieId, cinemaId, date, includePast);
        return ResponseEntity.ok(sessionService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.findById(id));
    }

    /** Mapa de assentos da sessão (público). */
    @GetMapping("/{id}/seats")
    public ResponseEntity<SeatMapResponseDTO> getSeatMap(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSeatMap(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmergency(@PathVariable Long id) {
        sessionService.deleteEmergency(id);
        return ResponseEntity.noContent().build();
    }

}
