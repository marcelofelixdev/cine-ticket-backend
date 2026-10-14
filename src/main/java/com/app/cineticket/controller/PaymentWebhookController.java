package com.app.cineticket.controller;

import com.app.cineticket.service.StripeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final StripeWebhookService webhookService;

    /**
     * Recebe o corpo cru (String): a assinatura é calculada sobre os bytes exatos enviados
     * pelo Stripe, então o JSON não pode ser reserializado antes da verificação.
     */
    @Operation(summary = "Webhook do Stripe",
            description = "Chamado pelo Stripe, não pelo frontend. Exige o cabeçalho Stripe-Signature.")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        webhookService.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
