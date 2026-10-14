package com.app.cineticket.controller;

import com.app.cineticket.dto.request.TeamMemberRequestDTO;
import com.app.cineticket.dto.response.TeamMemberResponseDTO;
import com.app.cineticket.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Equipe da rede (só ADMIN): gerentes e funcionários de todos os cinemas. */
@RestController
@RequestMapping("/api/v1/admin/team")
@RequiredArgsConstructor
public class AdminTeamController {

    private final TeamService teamService;

    @Operation(summary = "Cadastra um gerente ou funcionário em um cinema (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "404", description = "Cinema não encontrado", content = @Content)
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado", content = @Content)
    @PostMapping
    public ResponseEntity<TeamMemberResponseDTO> create(@RequestBody @Valid TeamMemberRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createByAdmin(request));
    }

    @Operation(summary = "Lista gerentes e funcionários (ADMIN), opcionalmente de um cinema")
    @GetMapping
    public ResponseEntity<List<TeamMemberResponseDTO>> list(@RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(teamService.listForAdmin(cinemaId));
    }
}
