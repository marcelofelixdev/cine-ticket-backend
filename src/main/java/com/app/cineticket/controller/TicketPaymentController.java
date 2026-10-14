package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.PaymentRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.service.PaymentService;
import com.app.cineticket.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
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
