package com.app.cineticket.service;

import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Marca como EXPIRED os ingressos PENDING cujo prazo de pagamento passou,
 * liberando o assento para outra compra.
 * <p>
 * É um UPDATE em lote e idempotente: pode rodar em mais de uma instância ao mesmo tempo.
 * A aprovação do pagamento continua exigindo status PENDING, então um ingresso
 * expirado não é mais aprovado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTicketExpirationJob {

    private final TicketRepository ticketRepository;

    @Scheduled(fixedDelayString = "${tickets.pending.expiration-check-interval-ms:60000}",
            initialDelayString = "${tickets.pending.expiration-check-interval-ms:60000}")
    @Transactional
    public void expirePendingTickets() {
        int expired = ticketRepository.expirePendingTickets(LocalDateTime.now());
        if (expired > 0) {
            log.info("[EXPIRATION] {} ingresso(s) pendente(s) expirado(s); assentos liberados.", expired);
        }
    }
}
