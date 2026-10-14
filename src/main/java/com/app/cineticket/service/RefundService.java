package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Refund;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.exception.PaymentGatewayException;
import com.app.cineticket.payment.PaymentGateway;
import com.app.cineticket.payment.RefundResult;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Executa um pedido de reembolso, uma única vez por pagamento.
 * <p>
 * Não lança exceção em falha do gateway: grava o reembolso como FAILED (com o erro e a
 * tentativa) e devolve o status. Assim o estado fica salvo mesmo quando o consumidor
 * pede uma nova tentativa ou manda a mensagem para a DLQ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final RefundRepository refundRepository;
    private final TicketRepository ticketRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public RefundStatus process(RefundEventDTO event) {
        Optional<Ticket> ticket = ticketRepository.findById(event.ticketId());
        if (ticket.isEmpty()) {
            log.warn("[REFUND] Ingresso {} não existe mais; reembolso descartado.", event.ticketId());
            return RefundStatus.NOT_APPLICABLE;
        }

        if (event.paymentIntentId() == null) {
            return registerNotApplicable(ticket.get(), event);
        }

        Refund refund = refundRepository.findByPaymentIntentId(event.paymentIntentId())
                .orElseGet(() -> newRefund(ticket.get(), event, RefundStatus.PENDING));

        if (refund.getStatus() == RefundStatus.REFUNDED) {
            log.info("[REFUND] Pagamento {} já foi estornado; mensagem repetida ignorada.", event.paymentIntentId());
            return RefundStatus.REFUNDED;
        }

        refund.setAttempts(refund.getAttempts() + 1);
        try {
            RefundResult result = refundOnGateway(event.paymentIntentId());
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setGatewayRefundId(result.refundId());
            refund.setRefundedAt(LocalDateTime.now(AppTimeZone.ZONE));
            refund.setLastError(null);
            log.info("[REFUND] Pagamento {} do ingresso {} estornado ({}).",
                    event.paymentIntentId(), event.ticketId(), result.refundId());
        } catch (PaymentGatewayException e) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setLastError(truncate(e.getMessage()));
            log.warn("[REFUND] Falha ao estornar o pagamento {} (tentativa {}): {}",
                    event.paymentIntentId(), refund.getAttempts(), e.getMessage());
        }
        refundRepository.save(refund);
        return refund.getStatus();
    }

    /** Pagamentos do simulador são sempre estornados pelo simulador, qualquer que seja o gateway atual. */
    private RefundResult refundOnGateway(String paymentIntentId) {
        if (paymentIntentId.startsWith("pi_mock_")) {
            return new RefundResult("re_mock_" + UUID.randomUUID());
        }
        return paymentGateway.refund(paymentIntentId);
    }

    private RefundStatus registerNotApplicable(Ticket ticket, RefundEventDTO event) {
        if (!refundRepository.existsByTicketIdAndPaymentIntentIdIsNull(ticket.getId())) {
            refundRepository.save(newRefund(ticket, event, RefundStatus.NOT_APPLICABLE));
        }
        log.info("[REFUND] Ingresso {} sem pagamento registrado no gateway; nada a estornar.", ticket.getId());
        return RefundStatus.NOT_APPLICABLE;
    }

    private static Refund newRefund(Ticket ticket, RefundEventDTO event, RefundStatus status) {
        Refund refund = new Refund();
        refund.setTicket(ticket);
        refund.setPaymentIntentId(event.paymentIntentId());
        refund.setAmount(event.valorEstorno() != null ? event.valorEstorno() : ticket.getValorPago());
        refund.setStatus(status);
        return refund;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
