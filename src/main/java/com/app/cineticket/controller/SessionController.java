package com.app.cineticket.controller;

import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "horarioInicio") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(sessionService.findAll(pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmergency(@PathVariable Long id) {
        sessionService.deleteEmergency(id);
        return ResponseEntity.noContent().build();
    }

}
