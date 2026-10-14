package com.app.cineticket.payment;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.exception.InvalidPaymentMethodException;
import com.app.cineticket.exception.PaymentGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/**
 * Cobrança real no Stripe, <b>somente em modo de teste</b> (chaves {@code sk_test_...}).
 * Cria e confirma o PaymentIntent na mesma chamada; a aprovação definitiva do ingresso
 * vem pelo webhook {@code payment_intent.succeeded}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    static final String INVALID_PAYMENT_METHOD = "O método de pagamento informado não existe nesta conta do Stripe. Em modo de teste, use pm_card_visa ou pm_card_chargeDeclined.";

    private final StripeClient client;
    private final String currency;

    public StripePaymentGateway(@Value("${payments.stripe.secret-key:}") String secretKey,
                                @Value("${payments.currency:brl}") String currency,
                                @Value("${payments.enabled:false}") boolean paymentsEnabled) {
        boolean hasKey = secretKey != null && !secretKey.isBlank();
        if (hasKey && !secretKey.startsWith("sk_test_")) {
            throw new IllegalStateException("Este projeto aceita apenas chaves de teste do Stripe (sk_test_...).");
        }
        if (paymentsEnabled && !hasKey) {
            throw new IllegalStateException("payments.provider=stripe exige a variável STRIPE_SECRET_KEY.");
        }
        this.client = hasKey ? new StripeClient(secretKey) : null;
        this.currency = currency;
    }

    @Override
    public PaymentResult charge(Ticket ticket, String paymentMethodId) {
        if (client == null) {
            throw new PaymentGatewayException("Stripe não configurado.", null);
        }
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(ticket))
                .setCurrency(currency)
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setDescription("CineTicket - ingresso " + ticket.getId())
                .putMetadata("ticketId", String.valueOf(ticket.getId()))
                // Sem redirecionamento: métodos que exigem sair do checkout ficam de fora.
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .build();
        // Repetir a mesma requisição (timeout, duplo clique) não gera uma segunda cobrança.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("ticket-" + ticket.getId() + "-" + paymentMethodId)
                .build();

        try {
            PaymentIntent intent = client.v1().paymentIntents().create(params, options);
            return switch (intent.getStatus()) {
                case "succeeded" -> PaymentResult.succeeded(intent.getId());
                case "processing" -> PaymentResult.processing(intent.getId());
                case "requires_action" -> PaymentResult.requiresAction(intent.getId());
                default -> PaymentResult.declined(intent.getId(), "Pagamento não autorizado.");
            };
        } catch (CardException e) {
            String reason = e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode();
            log.info("[STRIPE] Cartão recusado para o ingresso {}: {}", ticket.getId(), reason);
            return PaymentResult.declined(null, "Cartão recusado pelo emissor (" + reason + ").");
        } catch (InvalidRequestException e) {
            if ("payment_method".equals(e.getParam()) || "resource_missing".equals(e.getCode())) {
                log.info("[STRIPE] Método de pagamento inválido para o ingresso {}: {}", ticket.getId(), e.getMessage());
                throw new InvalidPaymentMethodException(INVALID_PAYMENT_METHOD);
            }
            log.error("[STRIPE] Requisição inválida ao cobrar o ingresso {}: {}", ticket.getId(), e.getMessage());
            throw new PaymentGatewayException("Não foi possível falar com o gateway de pagamento. Tente novamente.", e);
        } catch (StripeException e) {
            log.error("[STRIPE] Falha ao cobrar o ingresso {}: {}", ticket.getId(), e.getMessage());
            throw new PaymentGatewayException("Não foi possível falar com o gateway de pagamento. Tente novamente.", e);
        }
    }

    @Override
    public boolean confirmsViaWebhook() {
        return true;
    }

    @Override
    public RefundResult refund(String paymentIntentId) {
        if (client == null) {
            throw new PaymentGatewayException("Stripe não configurado.", null);
        }
        com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("refund-" + paymentIntentId)
                .build();
        try {
            com.stripe.model.Refund refund = client.v1().refunds().create(params, options);
            return new RefundResult(refund.getId());
        } catch (InvalidRequestException e) {
            if ("charge_already_refunded".equals(e.getCode())) {
                // Já estornado (por outra tentativa ou pelo painel): o objetivo foi atingido.
                log.info("[STRIPE] Pagamento {} já estava estornado.", paymentIntentId);
                return new RefundResult(null);
            }
            throw new PaymentGatewayException("O Stripe recusou o estorno: " + e.getMessage(), e);
        } catch (StripeException e) {
            throw new PaymentGatewayException("Não foi possível estornar no Stripe: " + e.getMessage(), e);
        }
    }

    /** O Stripe trabalha na menor unidade da moeda (centavos). */
    private static long toMinorUnits(Ticket ticket) {
        return ticket.getValorPago().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
