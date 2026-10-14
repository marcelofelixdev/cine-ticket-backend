package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.PaymentRequestDTO;
import com.app.cineticket.service.PaymentService;
import com.app.cineticket.service.RateLimitService;
import com.app.cineticket.dto.response.TicketResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketPaymentController {

    private final PaymentService paymentService;
    private final RateLimitService rateLimitService;

    @Operation(summary = "Paga um ingresso reservado",
            description = "Responde 202: a aprovação é assíncrona (acompanhe em GET /api/v1/tickets/{id}). "
                    + "Em modo de teste use paymentMethodId = pm_card_visa (aprovado) ou "
                    + "pm_card_chargeDeclined (recusado).")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pagamento aceito; o ingresso continua PENDING até a confirmação",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "O ingresso é de outro usuário", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ingresso não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ingresso não está aguardando pagamento, expirou ou já foi pago",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Cartão recusado, exige 3-D Secure ou método de pagamento inválido",
                    content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas de pagamento por minuto", content = @Content),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação com o gateway de pagamento", content = @Content)
    })
    @PostMapping("/{id}/payment")
    public ResponseEntity<TicketResponseDTO> pay(@PathVariable Long id,
                                                 @RequestBody @Valid PaymentRequestDTO request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!rateLimitService.getUserBucket("payment:" + auth.getName()).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Você atingiu o limite de tentativas de pagamento por minuto. Tente novamente mais tarde.");
        }
        User loggedUser = (User) auth.getPrincipal();
        return ResponseEntity.accepted().body(paymentService.pay(id, request.paymentMethodId(), loggedUser));
    }
}
