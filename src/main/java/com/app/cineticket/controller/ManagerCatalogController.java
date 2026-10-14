package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.MovieRequestCreateDTO;
import com.app.cineticket.dto.response.MovieRequestResponseDTO;
import com.app.cineticket.dto.response.ProgramacaoItemDTO;
import com.app.cineticket.service.MovieRequestService;
import com.app.cineticket.service.ProgramacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Filmes do cinema do gerente logado (só MANAGER): pedidos e programação. */
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerCatalogController {

    private final MovieRequestService movieRequestService;
    private final ProgramacaoService programacaoService;

    @Operation(summary = "Pede um filme do TMDB para o próprio cinema (MANAGER)",
            description = "O tmdbId vem de GET /tmdb/search. O pedido fica aguardando o admin.")
    @ApiResponse(responseCode = "201", description = "Pedido criado")
    @ApiResponse(responseCode = "404", description = "tmdbId não existe no TMDB", content = @Content)
    @ApiResponse(responseCode = "409", description = "Filme já no cinema ou pedido já aguardando", content = @Content)
    @ApiResponse(responseCode = "502", description = "TMDB indisponível", content = @Content)
    @PostMapping("/movie-requests")
    public ResponseEntity<MovieRequestResponseDTO> request(@RequestBody @Valid MovieRequestCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieRequestService.create(request, loggedUser()));
    }

    @Operation(summary = "Pedidos do próprio cinema (MANAGER)")
    @GetMapping("/movie-requests")
    public ResponseEntity<List<MovieRequestResponseDTO>> requests() {
        return ResponseEntity.ok(movieRequestService.listForManager(loggedUser()));
    }

    @Operation(summary = "Programação do próprio cinema (MANAGER)",
            description = "Filmes ativos e os enviados pelo admin que aguardam confirmação.")
    @GetMapping("/programacao")
    public ResponseEntity<List<ProgramacaoItemDTO>> programacao() {
        return ResponseEntity.ok(programacaoService.listForManager(loggedUser()));
    }

    @Operation(summary = "Confirma um filme enviado pelo admin (MANAGER)")
    @ApiResponse(responseCode = "200", description = "Filme ativo no cinema")
    @ApiResponse(responseCode = "404", description = "Filme não foi enviado para este cinema", content = @Content)
    @PostMapping("/programacao/{movieId}/confirm")
    public ResponseEntity<ProgramacaoItemDTO> confirm(@PathVariable Long movieId) {
        return ResponseEntity.ok(programacaoService.confirm(movieId, loggedUser()));
    }

    private static User loggedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
