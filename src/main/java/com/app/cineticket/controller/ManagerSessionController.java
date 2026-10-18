package com.app.cineticket.controller;

import com.app.cineticket.demo.DemoRestrictions;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.ManagerSessionDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import com.app.cineticket.service.CinemaScope;
import com.app.cineticket.service.ManagerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final DemoRestrictions demoRestrictions;

    @Operation(summary = "Sessões do dia no cinema, com ocupação, meias e receita (MANAGER ou ADMIN)",
            description = "Sem date, hoje. Inclui as que já começaram. " + CinemaScope.CINEMA_ID_DOC)
    @GetMapping("/sessions")
    public ResponseEntity<List<ManagerSessionDTO>> sessions(@AuthenticationPrincipal User loggedUser, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                            @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(managerSessionService.listOfDay(date, CinemaScope.actingCinemaId(loggedUser, cinemaId), true));
    }

    @Operation(summary = "Cria uma sessão no cinema (MANAGER ou ADMIN)",
            description = "A sala tem de ser do cinema e o filme tem de estar em cartaz nele. " + CinemaScope.CINEMA_ID_DOC)
    @ApiResponse(responseCode = "201", description = "Criada")
    @ApiResponse(responseCode = "403", description = "Sala de outro cinema", content = @Content)
    @ApiResponse(responseCode = "409", description = "Conflito de horário na sala", content = @Content)
    @ApiResponse(responseCode = "422", description = "Filme não está em cartaz no cinema", content = @Content)
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponseDTO> create(@AuthenticationPrincipal User loggedUser, @RequestBody @Valid SessionRequestDTO request,
                                                     @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerSessionService.create(request, CinemaScope.actingCinemaId(loggedUser, cinemaId)));
    }

    @Operation(summary = "Cancela uma sessão do cinema (MANAGER ou ADMIN)",
            description = "Cancela os ingressos e estorna os pagos, como o cancelamento emergencial do admin. "
                    + CinemaScope.CINEMA_ID_DOC)
    @ApiResponse(responseCode = "204", description = "Cancelada")
    @ApiResponse(responseCode = "403", description = "Sessão de outro cinema ou demonstração pública", content = @Content)
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                       @RequestParam(required = false) Long cinemaId) {
        demoRestrictions.requireNotDemo("Cancelar sessões");
        managerSessionService.cancel(id, CinemaScope.actingCinemaId(loggedUser, cinemaId));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Salas do cinema (MANAGER ou ADMIN)", description = CinemaScope.CINEMA_ID_DOC)
    @GetMapping("/rooms")
    public ResponseEntity<List<RoomResponseDTO>> rooms(@AuthenticationPrincipal User loggedUser, @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(managerSessionService.rooms(CinemaScope.actingCinemaId(loggedUser, cinemaId)));
    }
}
