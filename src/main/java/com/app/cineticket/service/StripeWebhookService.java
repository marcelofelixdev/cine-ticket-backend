package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.ProcessedWebhookEvent;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.exception.InvalidWebhookSignatureException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.repository.ProcessedWebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Webhook do Stripe.
 * <ol>
 *   <li>Verifica a assinatura ({@code Stripe-Signature}, HMAC-SHA256 com o signing secret
 *       e tolerância de tempo contra replay).</li>
 *   <li>Deduplica pelo ID do evento: o Stripe pode reenviar o mesmo evento.</li>
 *   <li>Grava a confirmação no outbox, na mesma transação, e responde 200 rápido.
 *       O consumidor do RabbitMQ faz o resto (aprovar ou reembolsar).</li>
 * </ol>
 * O payload é lido como JSON puro, sem depender da versão da API fixada no SDK.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    /** Campos dos metadados que o gateway grava no PaymentIntent (ver Charge). */
    private static final String METADATA = "metadata";
    private static final String TICKET_ID = "ticketId";
    private static final String ORDER_ID = "orderId";

    private final ProcessedWebhookEventRepository processedEvents;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${payments.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${payments.stripe.webhook-tolerance-seconds:300}")
    private long toleranceSeconds = 300;

    @Transactional
    public void handle(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResourceNotFoundException("Webhook de pagamento não configurado neste ambiente.");
        }
        verifySignature(payload, signatureHeader);

        JsonNode event = parse(payload);
        String eventId = event.path("id").asText(null);
        String type = event.path("type").asText("");
        if (eventId == null) {
            throw new InvalidWebhookSignatureException("Evento sem identificador.");
        }

        if (processedEvents.existsById(eventId)) {
            log.info("[WEBHOOK] Evento {} ({}) já processado; ignorado.", eventId, type);
            return;
        }
        processedEvents.save(new ProcessedWebhookEvent(eventId, type, LocalDateTime.now(AppTimeZone.ZONE)));

        JsonNode intent = event.path("data").path("object");
        switch (type) {
            case "payment_intent.succeeded" -> onSucceeded(eventId, intent);
            case "payment_intent.payment_failed" -> log.info("[WEBHOOK] Pagamento recusado: {} (ingresso {}, pedido {}).",
                    intent.path("id").asText(), intent.path(METADATA).path(TICKET_ID).asText("-"),
                    intent.path(METADATA).path(ORDER_ID).asText("-"));
            default -> log.debug("[WEBHOOK] Evento {} ignorado.", type);
        }
    }

    private void onSucceeded(String eventId, JsonNode intent) {
        String paymentIntentId = intent.path("id").asText(null);
        String ticketId = intent.path(METADATA).path(TICKET_ID).asText(null);
        String orderId = intent.path(METADATA).path(ORDER_ID).asText(null);
        boolean isOrder = orderId != null && orderId.matches("\\d+");
        boolean isTicket = ticketId != null && ticketId.matches("\\d+");
        if (paymentIntentId == null || (!isOrder && !isTicket)) {
            log.warn("[WEBHOOK] Evento {} sem ticketId nem orderId nos metadados; ignorado.", eventId);
            return;
        }
        long amountInCents = intent.hasNonNull("amount_received")
                ? intent.path("amount_received").asLong()
                : intent.path("amount").asLong();
        BigDecimal amount = BigDecimal.valueOf(amountInCents, 2);

        if (isOrder) {
            outboxService.paymentConfirmed(PaymentEventDTO.ofOrder(Long.valueOf(orderId), paymentIntentId, amount));
            log.info("[WEBHOOK] Pagamento {} confirmado para o pedido {}.", paymentIntentId, orderId);
        } else {
            outboxService.paymentConfirmed(PaymentEventDTO.ofTicket(Long.valueOf(ticketId), paymentIntentId, amount));
            log.info("[WEBHOOK] Pagamento {} confirmado para o ingresso {}.", paymentIntentId, ticketId);
        }
    }

    private void verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("Cabeçalho Stripe-Signature ausente.");
        }
        try {
            Webhook.Signature.verifyHeader(payload, signatureHeader, webhookSecret, toleranceSeconds);
        } catch (SignatureVerificationException e) {
            log.warn("[WEBHOOK] Assinatura inválida: {}", e.getMessage());
            throw new InvalidWebhookSignatureException("A assinatura do webhook não confere.");
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new InvalidWebhookSignatureException("Payload do webhook não é um JSON válido.");
        }
    }
}
