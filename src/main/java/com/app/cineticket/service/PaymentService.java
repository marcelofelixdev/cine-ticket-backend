package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.PaymentDeclinedException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.payment.PaymentGateway;
import com.app.cineticket.payment.PaymentOutcome;
import com.app.cineticket.payment.PaymentResult;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Pagamento de um ingresso reservado (PENDING).
 * <p>
 * A resposta é 202: a cobrança foi aceita, mas o ingresso só vira APPROVED quando a
 * confirmação passa pelo outbox e pelo consumidor do RabbitMQ. No Stripe, a confirmação
 * é o webhook {@code payment_intent.succeeded}; no simulador, sai daqui mesmo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TicketRepository ticketRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxService outboxService;
    private final TicketMapper ticketMapper;

    @Value("${payments.enabled:false}")
    private boolean paymentsEnabled;

    @Transactional
    public TicketResponseDTO pay(Long ticketId, String paymentMethodId, User loggedUser) {
        if (!paymentsEnabled) {
            throw new BusinessException("Pagamentos indisponíveis neste ambiente.");
        }

        // Lock no ingresso: dois pagamentos simultâneos do mesmo ingresso são serializados.
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }
        if (ticket.getStatus() != TicketStatus.PENDING) {
            throw new ConflictException("Este ingresso não está aguardando pagamento.");
        }
        if (ticket.getExpiresAt() != null && !ticket.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ConflictException("O prazo de pagamento deste ingresso expirou.");
        }
        if (ticket.getPaymentIntentId() != null) {
            throw new ConflictException("O pagamento deste ingresso já foi enviado. "
                    + "Acompanhe a aprovação em GET /api/v1/tickets/" + ticketId + ".");
        }

        PaymentResult result = paymentGateway.charge(ticket, paymentMethodId);

        switch (result.outcome()) {
            case DECLINED -> throw new PaymentDeclinedException(result.message()
                    + " O assento continua reservado até o fim do prazo de pagamento; tente outro cartão.");
            case REQUIRES_ACTION -> throw new PaymentDeclinedException(
                    "Este cartão exige autenticação adicional (3-D Secure), que ainda não é suportada. Use outro cartão.");
            case SUCCEEDED, PROCESSING -> {
                ticket.setPaymentIntentId(result.paymentIntentId());
                ticketRepository.save(ticket);
                if (!paymentGateway.confirmsViaWebhook() && result.outcome() == PaymentOutcome.SUCCEEDED) {
                    outboxService.paymentConfirmed(new PaymentEventDTO(
                            ticket.getId(), result.paymentIntentId(), ticket.getValorPago()));
                }
                log.info("[PAYMENT] Ingresso {} cobrado ({}), aguardando confirmação.",
                        ticket.getId(), result.outcome());
            }
        }

        return ticketMapper.toResponseDTO(ticket);
    }
}
