package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.ManagerSessionDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.service.ManagerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Sessões e salas do cinema do gerente logado (só MANAGER). */
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerSessionController {

    private final ManagerSessionService managerSessionService;

    @Operation(summary = "Sessões do dia no próprio cinema, com ocupação (MANAGER)",
            description = "Sem date, hoje. Inclui as que já começaram.")
    @GetMapping("/sessions")
    public ResponseEntity<List<ManagerSessionDTO>> sessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(managerSessionService.listOfDay(date, loggedUser()));
    }

    @Operation(summary = "Cria uma sessão no próprio cinema (MANAGER)",
            description = "A sala tem de ser do cinema do gerente e o filme tem de estar em cartaz nele.")
    @ApiResponse(responseCode = "201", description = "Criada")
    @ApiResponse(responseCode = "403", description = "Sala de outro cinema", content = @Content)
    @ApiResponse(responseCode = "409", description = "Conflito de horário na sala", content = @Content)
    @ApiResponse(responseCode = "422", description = "Filme não está em cartaz no cinema", content = @Content)
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponseDTO> create(@RequestBody @Valid SessionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(managerSessionService.create(request, loggedUser()));
    }

    @Operation(summary = "Cancela uma sessão do próprio cinema (MANAGER)",
            description = "Cancela os ingressos e estorna os pagos, como o cancelamento emergencial do admin.")
    @ApiResponse(responseCode = "204", description = "Cancelada")
    @ApiResponse(responseCode = "403", description = "Sessão de outro cinema", content = @Content)
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        managerSessionService.cancel(id, loggedUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Salas do próprio cinema (MANAGER)")
    @GetMapping("/rooms")
    public ResponseEntity<List<RoomResponseDTO>> rooms() {
        return ResponseEntity.ok(managerSessionService.rooms(loggedUser()));
    }

    private static User loggedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
