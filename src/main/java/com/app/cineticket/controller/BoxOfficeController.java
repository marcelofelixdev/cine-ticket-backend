package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.BoxOfficeSaleRequestDTO;
import com.app.cineticket.dto.response.BoxOfficeSaleResponseDTO;
import com.app.cineticket.dto.response.CounterRefundDTO;
import com.app.cineticket.dto.response.ManagerSessionDTO;
import com.app.cineticket.service.BoxOfficeService;
import com.app.cineticket.service.CounterRefundService;
import com.app.cineticket.service.ManagerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Caixa do cinema (STAFF ou MANAGER, sempre no próprio cinema). O mapa de assentos da
 * sessão é o público: GET /api/v1/sessions/{id}/seats.
 */
@RestController
@RequestMapping("/api/v1/box-office")
@RequiredArgsConstructor
public class BoxOfficeController {

    private final BoxOfficeService boxOfficeService;
    private final ManagerSessionService managerSessionService;
    private final CounterRefundService counterRefundService;

    @Operation(summary = "Sessões do dia no próprio cinema, com ocupação (STAFF ou MANAGER)")
    @GetMapping("/sessions")
    public ResponseEntity<List<ManagerSessionDTO>> sessions(@AuthenticationPrincipal User loggedUser, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(managerSessionService.listOfDay(date, loggedUser));
    }

    @Operation(summary = "Vende lugares no caixa (STAFF ou MANAGER)",
            description = "Até 10 lugares de uma sessão do próprio cinema, pagos na hora (dinheiro, crédito, débito ou Pix). "
                    + "Os ingressos já saem aprovados, com o QR para imprimir.")
    @ApiResponse(responseCode = "201", description = "Venda feita")
    @ApiResponse(responseCode = "403", description = "Sessão de outro cinema", content = @Content)
    @ApiResponse(responseCode = "409", description = "Lugar ocupado ou cota de meia esgotada", content = @Content)
    @ApiResponse(responseCode = "422", description = "Vendas encerradas, lugar de outra sala ou documento de estudante ausente", content = @Content)
    @PostMapping("/sales")
    public ResponseEntity<BoxOfficeSaleResponseDTO> sell(@AuthenticationPrincipal User loggedUser, @RequestBody @Valid BoxOfficeSaleRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boxOfficeService.sell(request, loggedUser));
    }

    @Operation(summary = "Devoluções pendentes no caixa do próprio cinema (STAFF ou MANAGER)",
            description = "Vendas de balcão de sessões canceladas: o cliente volta ao caixa para receber o valor.")
    @GetMapping("/refunds/pending")
    public ResponseEntity<List<CounterRefundDTO>> pendingRefunds(@AuthenticationPrincipal User loggedUser) {
        return ResponseEntity.ok(counterRefundService.pending(loggedUser));
    }

    @Operation(summary = "Confirma que o caixa devolveu o valor ao cliente (STAFF ou MANAGER)")
    @ApiResponse(responseCode = "200", description = "Devolução registrada (REFUNDED), com quem devolveu")
    @ApiResponse(responseCode = "403", description = "Ingresso de outro cinema", content = @Content)
    @ApiResponse(responseCode = "404", description = "Não há devolução pendente para o ingresso", content = @Content)
    @PostMapping("/refunds/{ticketId}/confirm")
    public ResponseEntity<CounterRefundDTO> confirmRefund(@AuthenticationPrincipal User loggedUser,
                                                          @PathVariable Long ticketId) {
        return ResponseEntity.ok(counterRefundService.confirm(ticketId, loggedUser));
    }

    @Operation(summary = "PDF de um ingresso vendido no balcão do próprio cinema (STAFF ou MANAGER)")
    @GetMapping(value = "/tickets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ingresso-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(boxOfficeService.pdf(id, loggedUser));
    }
}
