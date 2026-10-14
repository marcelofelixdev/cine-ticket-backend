package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.MovieRequestStatus;
import com.app.cineticket.dto.request.MovieOfferDTO;
import com.app.cineticket.dto.request.MovieRequestRejectDTO;
import com.app.cineticket.dto.response.AdminMovieDTO;
import com.app.cineticket.dto.response.CinemaProgramacaoDTO;
import com.app.cineticket.dto.response.MovieRequestResponseDTO;
import com.app.cineticket.service.MovieRequestService;
import com.app.cineticket.service.ProgramacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo da rede (só ADMIN): pedidos dos gerentes e envio de filmes para os cinemas. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final MovieRequestService movieRequestService;
    private final ProgramacaoService programacaoService;

    @Operation(summary = "Pedidos de filme dos gerentes (ADMIN)", description = "Sem status, todos; mais recentes primeiro.")
    @GetMapping("/movie-requests")
    public ResponseEntity<List<MovieRequestResponseDTO>> listRequests(
            @RequestParam(required = false) MovieRequestStatus status) {
        return ResponseEntity.ok(movieRequestService.listForAdmin(status));
    }

    @Operation(summary = "Aprova um pedido (ADMIN)",
            description = "Cadastra o filme pelo TMDB (ou reaproveita o do catálogo) e deixa ativo no cinema do gerente.")
    @ApiResponse(responseCode = "200", description = "Aprovado")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    @ApiResponse(responseCode = "409", description = "Pedido já decidido", content = @Content)
    @ApiResponse(responseCode = "502", description = "TMDB indisponível", content = @Content)
    @PostMapping("/movie-requests/{id}/approve")
    public ResponseEntity<MovieRequestResponseDTO> approve(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        return ResponseEntity.ok(movieRequestService.approve(id, loggedUser));
    }

    @Operation(summary = "Recusa um pedido com motivo (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Recusado")
    @ApiResponse(responseCode = "409", description = "Pedido já decidido", content = @Content)
    @ApiResponse(responseCode = "422", description = "Motivo em branco", content = @Content)
    @PostMapping("/movie-requests/{id}/reject")
    public ResponseEntity<MovieRequestResponseDTO> reject(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                                          @RequestBody @Valid MovieRequestRejectDTO request) {
        return ResponseEntity.ok(movieRequestService.reject(id, request, loggedUser));
    }

    @Operation(summary = "Catálogo inteiro com os cinemas de cada filme (ADMIN)",
            description = "Inclui filmes fora de cartaz, que não aparecem em GET /movies.")
    @GetMapping("/movies")
    public ResponseEntity<Page<AdminMovieDTO>> listMovies(
            @ParameterObject @PageableDefault(size = 20, sort = "titulo") Pageable pageable) {
        return ResponseEntity.ok(programacaoService.listForAdmin(pageable));
    }

    @Operation(summary = "Envia um filme para cinemas (ADMIN)",
            description = "Em cada cinema o filme fica aguardando o gerente confirmar. Cinemas que já têm o filme ficam como estão.")
    @ApiResponse(responseCode = "200", description = "Situação do filme em cada cinema")
    @ApiResponse(responseCode = "404", description = "Filme ou cinema não encontrado", content = @Content)
    @PostMapping("/movies/{id}/offer")
    public ResponseEntity<List<CinemaProgramacaoDTO>> offer(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                                            @RequestBody @Valid MovieOfferDTO request) {
        return ResponseEntity.ok(programacaoService.offer(id, request, loggedUser));
    }
}
