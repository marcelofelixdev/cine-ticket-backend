package com.app.cineticket.controller;

import com.app.cineticket.dto.response.TmdbCandidateDTO;
import com.app.cineticket.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Busca de filmes no TMDB para o painel (ADMIN ou MANAGER): o usuário digita o nome e escolhe o certo. */
@RestController
@RequestMapping("/api/v1/tmdb")
@RequiredArgsConstructor
public class TmdbSearchController {

    private static final int MIN_QUERY = 2;
    private static final int MAX_QUERY = 100;

    private final MovieService movieService;

    @Operation(summary = "Pesquisa filmes no TMDB (ADMIN ou MANAGER)",
            description = "Até 10 candidatos com pôster e ano. filmeId vem preenchido se o filme já está no catálogo.")
    @ApiResponse(responseCode = "200", description = "Candidatos (lista vazia se nada for encontrado)")
    @ApiResponse(responseCode = "400", description = "Busca com menos de 2 ou mais de 100 caracteres", content = @Content)
    @ApiResponse(responseCode = "502", description = "TMDB indisponível", content = @Content)
    @GetMapping("/search")
    public ResponseEntity<List<TmdbCandidateDTO>> search(@RequestParam("q") String query) {
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY || trimmed.length() > MAX_QUERY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Digite de " + MIN_QUERY + " a " + MAX_QUERY + " caracteres para pesquisar.");
        }
        return ResponseEntity.ok(movieService.searchTmdb(trimmed));
    }
}
