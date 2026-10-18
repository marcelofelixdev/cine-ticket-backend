package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Regras da meia-entrada compartilhadas pela compra online e pelo caixa: preço, carteira
 * de estudante e cota de 40% dos lugares por sessão (Lei 12.933/2013).
 */
@Component
@RequiredArgsConstructor
public class HalfPricePolicy {

    public static final List<TicketType> HALF_PRICE_TYPES = List.of(TicketType.MEIA_ESTUDANTE, TicketType.MEIA_IDOSO);
    private static final int MIN_STUDENT_DOCUMENT = 6;

    private final TicketRepository ticketRepository;

    /** Fração dos lugares de cada sessão que pode ser vendida como meia-entrada. */
    @Value("${tickets.half-price.quota:0.40}")
    private double quota = 0.40;

    public static boolean isHalfPrice(TicketType type) {
        return HALF_PRICE_TYPES.contains(type);
    }

    /** Inteira paga o valor da sessão; meia, a metade. */
    public static BigDecimal priceFor(BigDecimal sessionPrice, TicketType type) {
        return isHalfPrice(type) ? sessionPrice.divide(new BigDecimal("2"), RoundingMode.HALF_UP) : sessionPrice;
    }

    /** Número da carteira de estudante normalizado (conferido na entrada). */
    public static String studentDocument(String raw) {
        String documento = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (documento.length() < MIN_STUDENT_DOCUMENT) {
            throw new BusinessException("Para meia-entrada de estudante, informe o número da carteira de estudante "
                    + "(documentoMeia). Ele será conferido na entrada.");
        }
        return documento;
    }

    /** Idade mínima da meia 60+, na data da sessão. */
    public static final int SENIOR_AGE = 60;

    /**
     * Meia 60+ na compra online: pela data de nascimento do perfil, com 60 anos ou mais na
     * data da sessão. No caixa o documento é conferido na hora.
     */
    public static void requireSenior(com.app.cineticket.domain.entity.User buyer, Session session) {
        if (buyer.getDataNascimento() == null) {
            throw new BusinessException("Para meia-entrada de idoso, informe sua data de nascimento no perfil "
                    + "(PATCH /api/v1/users/me).");
        }
        int age = java.time.Period.between(buyer.getDataNascimento(),
                session.getHorarioInicio().toLocalDate()).getYears();
        if (age < SENIOR_AGE) {
            throw new BusinessException("Meia-entrada de idoso é para quem tem " + SENIOR_AGE
                    + " anos ou mais na data da sessão.");
        }
    }

    public static int capacityOf(Session session) {
        return session.getRoom().getCapacidade() == null ? 0 : session.getRoom().getCapacidade();
    }

    /** Quantas meias a sessão aceita no total (40% dos lugares, arredondado para baixo). */
    public long limitFor(Session session) {
        return (long) Math.floor(capacityOf(session) * quota);
    }

    /** Quantas meias ainda cabem, dadas as já vendidas ou reservadas. */
    public long remaining(Session session, long sold) {
        return Math.max(0, limitFor(session) - sold);
    }

    /**
     * Confere se ainda cabem {@code requested} meias na sessão. Quem chama precisa ter travado
     * a sessão (lock pessimista), senão duas vendas simultâneas podem estourar a cota.
     */
    public void requireQuota(Session session, long requested) {
        if (requested <= 0) {
            return;
        }
        int capacity = capacityOf(session);
        long limit = limitFor(session);
        long sold = ticketRepository.countBySessionIdAndTicketTypeInAndStatusIn(
                session.getId(), HALF_PRICE_TYPES, TicketStatus.OCCUPYING_SEAT);
        if (sold + requested <= limit) {
            return;
        }
        long left = Math.max(0, limit - sold);
        throw new ConflictException(left == 0
                ? "Cota de meia-entrada esgotada para esta sessão (" + limit + " de " + capacity + " lugares). "
                        + "A inteira continua disponível."
                : "Cota de meia-entrada insuficiente para esta sessão: restam " + left + " de " + limit + ". "
                        + "A inteira continua disponível.");
    }
}
