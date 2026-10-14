package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.TicketValidationRequestDTO;
import com.app.cineticket.dto.response.TicketQrCodeDTO;
import com.app.cineticket.dto.response.TicketValidationResponseDTO;
import com.app.cineticket.service.TicketValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingresso na porta da sala: o cliente mostra o QR e o funcionário (STAFF ou ADMIN) valida. */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketEntryController {

    private final TicketValidationService validationService;

    @Operation(summary = "Conteúdo do QR Code do próprio ingresso",
            description = "Para o frontend desenhar o ingresso digital. Só para ingressos aprovados.")
    @GetMapping("/{id}/qr")
    public ResponseEntity<TicketQrCodeDTO> qrCode(@PathVariable Long id) {
        User loggedUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(validationService.qrCodeFor(id, loggedUser));
    }

    @Operation(summary = "Valida o ingresso na entrada (STAFF ou ADMIN)",
            description = "Confere a assinatura do QR, o status e o horário, e marca o ingresso como usado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada liberada",
                    content = @Content(schema = @Schema(implementation = TicketValidationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "QR Code inválido ou adulterado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Usuário sem papel STAFF/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ingresso não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ingresso já utilizado, cancelado ou não pago", content = @Content),
            @ApiResponse(responseCode = "422", description = "Fora do horário da sessão", content = @Content)
    })
    @PostMapping("/validate")
    public ResponseEntity<TicketValidationResponseDTO> validate(@RequestBody @Valid TicketValidationRequestDTO request) {
        User staff = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(validationService.validate(request.qr(), staff));
    }
}
