package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.OrderRequestDTO;
import com.app.cineticket.dto.request.PaymentRequestDTO;
import com.app.cineticket.dto.response.OrderResponseDTO;
import com.app.cineticket.service.OrderService;
import com.app.cineticket.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RateLimitService rateLimitService;

    @Operation(summary = "Reserva os lugares de uma sessão (todos ou nenhum)",
            description = "Os lugares ficam reservados até expiresAt. O máximo de lugares por pedido está em "
                    + "maxLugaresPorPedido, no mapa da sala (GET /sessions/{id}/seats).")
    @ApiResponse(responseCode = "201", description = "Pedido criado, aguardando pagamento")
    @ApiResponse(responseCode = "409", description = "Lugar já ocupado ou cota de meia esgotada", content = @Content)
    @ApiResponse(responseCode = "422", description = "Sessão encerrada, lugares demais, meia sem carteira ou idade",
            content = @Content)
    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(@AuthenticationPrincipal User loggedUser,
                                                   @RequestBody @Valid OrderRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, loggedUser));
    }

    @Operation(summary = "Pedido do próprio usuário (acompanhar a aprovação do pagamento)")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> findById(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id, loggedUser));
    }

    @Operation(summary = "Paga o pedido inteiro numa cobrança só",
            description = "Responde 202: a aprovação é assíncrona (acompanhe em GET /api/v1/orders/{id}). "
                    + "Em modo de teste use pm_card_visa (aprovado) ou pm_card_chargeDeclined (recusado).")
    @ApiResponse(responseCode = "202", description = "Pagamento aceito; o pedido continua PENDING até a confirmação")
    @ApiResponse(responseCode = "409", description = "Pedido expirado, já pago ou com pagamento em andamento",
            content = @Content)
    @ApiResponse(responseCode = "422", description = "Cartão recusado, exige 3-D Secure ou método inválido",
            content = @Content)
    @ApiResponse(responseCode = "429", description = "Muitas tentativas de pagamento por minuto", content = @Content)
    @PostMapping("/{id}/payment")
    public ResponseEntity<OrderResponseDTO> pay(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                                @RequestBody @Valid PaymentRequestDTO request) {
        if (!rateLimitService.getUserBucket("payment:" + loggedUser.getEmail()).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Você atingiu o limite de tentativas de pagamento por minuto. Tente novamente mais tarde.");
        }
        return ResponseEntity.accepted().body(orderService.pay(id, request.paymentMethodId(), loggedUser));
    }
}
