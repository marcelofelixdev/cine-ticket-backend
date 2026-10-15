package com.app.cineticket.controller;

import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.request.MovieUpdateDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import com.app.cineticket.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "Cadastra um filme (ADMIN)",
            description = "Normalmente só com tmdbId (escolhido na busca do TMDB): título, duração, elenco, "
                    + "classificação e o resto vêm de lá. Sem tmdbId, título e duração são obrigatórios.")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "404", description = "tmdbId não existe no TMDB", content = @Content)
    @ApiResponse(responseCode = "409", description = "Filme já está no catálogo", content = @Content)
    @ApiResponse(responseCode = "502", description = "TMDB indisponível", content = @Content)
    @PostMapping
    public ResponseEntity<MovieResponseDTO> create(@RequestBody @Valid MovieRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.create(requestDTO));
    }

    @Operation(summary = "Filmes em cartaz (público)",
            description = "Ativos em algum cinema e já estreados. Filtros opcionais: cinemaId ou cidade; "
                    + "formato e audio (só filmes com sessão futura assim); classificacao.")
    @GetMapping
    public ResponseEntity<Page<MovieResponseDTO>> findAll(
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "2D|3D") String formato,
            @RequestParam(required = false) com.app.cineticket.domain.enums.SessionAudio audio,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "L|10|12|14|16|18")
            String classificacao,
            @ParameterObject @PageableDefault(size = 10, page = 0, sort = "titulo") Pageable pageable) {
        return ResponseEntity.ok(movieService.findAll(pageable,
                new com.app.cineticket.dto.request.MovieFilter(cinemaId, cidade, formato, audio, classificacao)));
    }

    @Operation(summary = "Em breve (público)",
            description = "Filmes programados em algum cinema com estreia depois de hoje. Filtro opcional: cidade.")
    @GetMapping("/em-breve")
    public ResponseEntity<Page<MovieResponseDTO>> comingSoon(
            @RequestParam(required = false) String cidade,
            @ParameterObject @PageableDefault(size = 10, page = 0, sort = "dataEstreia") Pageable pageable) {
        return ResponseEntity.ok(movieService.findComingSoon(pageable, cidade));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.findById(id));
    }

    @Operation(summary = "Corrige dados do filme (ADMIN); só os campos enviados mudam")
    @PutMapping("/{id}")
    public ResponseEntity<MovieResponseDTO> update(@PathVariable Long id, @RequestBody @Valid MovieUpdateDTO requestDTO) {
        return ResponseEntity.ok(movieService.update(id, requestDTO));
    }

    @Operation(summary = "Atualiza a ficha do filme a partir do TMDB (ADMIN)",
            description = "Busca de novo elenco, direção, classificação, trailer e imagens. Título e duração não mudam.")
    @ApiResponse(responseCode = "200", description = "Atualizado")
    @ApiResponse(responseCode = "422", description = "Filme sem vínculo com o TMDB", content = @Content)
    @ApiResponse(responseCode = "502", description = "TMDB indisponível", content = @Content)
    @PostMapping("/{id}/tmdb-sync")
    public ResponseEntity<MovieResponseDTO> syncFromTmdb(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.syncFromTmdb(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
