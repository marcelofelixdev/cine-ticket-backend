package com.app.cineticket.service;

import java.math.BigDecimal;

/**
 * Ocupação de uma sessão: lugares ocupados (reservados ou pagos), quantos deles são meia
 * e a receita dos já pagos.
 */
public record SessionOccupancy(long ocupados, long meias, BigDecimal receita) {

    public static final SessionOccupancy EMPTY = new SessionOccupancy(0, 0, BigDecimal.ZERO);

    SessionOccupancy plus(SessionOccupancy other) {
        return new SessionOccupancy(ocupados + other.ocupados, meias + other.meias, receita.add(other.receita));
    }
}
