package com.app.cineticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pagamento de um ingresso reservado. O {@code paymentMethodId} é gerado pelo Stripe
 * (Stripe.js/Elements no frontend); o número do cartão nunca chega à API.
 * No modo de teste valem os IDs prontos do Stripe, como {@code pm_card_visa}
 * (aprovado) e {@code pm_card_chargeDeclined} (recusado).
 */
public record PaymentRequestDTO(
        @NotBlank(message = "O método de pagamento é obrigatório")
        @Size(max = 255, message = "O método de pagamento excede o tamanho permitido")
        @Pattern(regexp = "^pm_\\w+$", message = "Método de pagamento inválido (esperado um ID pm_...)")
        String paymentMethodId
) {}
