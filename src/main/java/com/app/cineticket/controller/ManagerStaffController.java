package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.StaffRequestDTO;
import com.app.cineticket.dto.response.TeamMemberResponseDTO;
import com.app.cineticket.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Funcionários do cinema do gerente logado (só MANAGER). */
@RestController
@RequestMapping("/api/v1/manager/staff")
@RequiredArgsConstructor
public class ManagerStaffController {

    private final TeamService teamService;

    @Operation(summary = "Cadastra um funcionário no cinema do gerente (MANAGER)")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "403", description = "Não é gerente ou está sem cinema vinculado", content = @Content)
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado", content = @Content)
    @PostMapping
    public ResponseEntity<TeamMemberResponseDTO> create(@RequestBody @Valid StaffRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createByManager(request, loggedUser()));
    }

    @Operation(summary = "Lista os funcionários do cinema do gerente (MANAGER)")
    @GetMapping
    public ResponseEntity<List<TeamMemberResponseDTO>> list() {
        return ResponseEntity.ok(teamService.listStaffOf(loggedUser()));
    }

    private static User loggedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
