package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Refund;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.dto.response.CounterRefundDTO;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.RefundRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Devoluções de vendas de balcão: não passam pelo gateway, o dinheiro volta no caixa.
 * Ficam PENDING quando a sessão é cancelada e viram REFUNDED quando o caixa devolve,
 * registrando quem devolveu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CounterRefundService {

    /** gateway_refund_id das devoluções feitas no caixa. */
    public static final String COUNTER_REFUND_ID = "BALCAO";

    private final RefundRepository refundRepository;
    private final UserRepository userRepository;

    /** Devolve na hora (cancelamento no caixa): já nasce REFUNDED com quem devolveu. */
    @Transactional
    public Refund refundNow(Ticket ticket, User cashier) {
        Refund refund = register(ticket, RefundStatus.REFUNDED);
        markPaidBack(refund, cashier);
        return refund;
    }

    /** Sessão cancelada: a devolução fica pendente até o cliente voltar ao caixa. */
    @Transactional
    public Refund registerPending(Ticket ticket) {
        return register(ticket, RefundStatus.PENDING);
    }

    /** Devoluções pendentes do cinema, da sessão mais antiga para a mais nova. */
    @Transactional(readOnly = true)
    public List<CounterRefundDTO> pending(User cashier) {
        return refundRepository.findPendingCounterRefunds(CinemaScope.cinemaIdOf(cashier), COUNTER_REFUND_ID).stream()
                .map(CounterRefundService::toDto)
                .toList();
    }

    /** O caixa devolveu o dinheiro de um ingresso do próprio cinema. */
    @Transactional
    public CounterRefundDTO confirm(Long ticketId, User cashier) {
        Refund refund = refundRepository.findPendingCounterRefundForUpdate(ticketId, COUNTER_REFUND_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Não há devolução pendente para este ingresso."));
        CinemaScope.requireSameCinema(cashier, refund.getTicket().getSession().getRoom().getCinema().getId(),
                "Este ingresso é de outro cinema.");
        markPaidBack(refund, cashier);
        log.info("[CAIXA] Devolução de R$ {} do ingresso {} feita por {}.", refund.getAmount(), ticketId,
                cashier.getEmail());
        return toDto(refund);
    }

    private Refund register(Ticket ticket, RefundStatus status) {
        Refund refund = RefundService.newRefund(ticket,
                new RefundEventDTO(ticket.getId(), null, ticket.getValorPago()), status);
        refund.setGatewayRefundId(COUNTER_REFUND_ID);
        Refund saved = refundRepository.save(refund);
        ticket.getRefunds().add(0, saved); // a resposta do ingresso já mostra o reembolso
        return saved;
    }

    private void markPaidBack(Refund refund, User cashier) {
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setRefundedAt(LocalDateTime.now(AppTimeZone.ZONE));
        refund.setRefundedBy(userRepository.getReferenceById(cashier.getId()));
        refundRepository.save(refund);
    }

    private static CounterRefundDTO toDto(Refund refund) {
        Ticket ticket = refund.getTicket();
        return new CounterRefundDTO(
                ticket.getId(),
                ticket.getSession().getMovie().getTitulo(),
                ticket.getSession().getRoom().getNome(),
                ticket.getSession().getHorarioInicio(),
                ticket.getSeat().label(),
                ticket.getFormaPagamento(),
                refund.getAmount(),
                ticket.getUser() != null ? ticket.getUser().getEmail() : ticket.getClienteEmail(),
                refund.getStatus(),
                refund.getRefundedAt());
    }
}
