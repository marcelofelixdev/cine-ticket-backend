package com.app.cineticket.domain.enums;

/** Situação de um assento em uma sessão, vista pelo mapa de assentos. */
public enum SeatAvailability {
    /** Pode ser comprado. */
    LIVRE,
    /** Tem um ingresso aprovado ou aguardando pagamento. */
    OCUPADO
}
