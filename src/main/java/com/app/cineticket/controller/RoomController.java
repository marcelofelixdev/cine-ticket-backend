package com.app.cineticket.controller;

import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponseDTO> create(@RequestBody @Valid RoomRequestDTO requestDTO) {
        RoomResponseDTO response = roomService.create(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<RoomResponseDTO>> findAll(
            @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "nome") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(roomService.findAll(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomResponseDTO> update(@PathVariable Long id, @RequestBody @Valid RoomRequestDTO requestDTO) {
        return ResponseEntity.ok(roomService.update(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}