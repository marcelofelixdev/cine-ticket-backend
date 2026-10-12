package com.app.cineticket.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Fuso horário único da aplicação: todos os cinemas operam no horário de Brasília.
 * <p>
 * As datas são {@code LocalDateTime} gravadas em {@code TIMESTAMP} sem fuso, então
 * "agora" precisa ser calculado no mesmo fuso em que o admin cadastra as sessões.
 * Sem isso, num container em UTC, as regras de compra, cancelamento e expiração
 * ficariam deslocadas em 3 horas.
 * <p>
 * Aplicado no {@code main}, antes de o Spring subir, para valer para Hibernate,
 * Jackson, logs, validação ({@code @Future}) e o driver do PostgreSQL, que usa o
 * fuso da JVM na sessão do banco.
 */
public final class AppTimeZone {

    public static final String ID = "America/Sao_Paulo";
    public static final ZoneId ZONE = ZoneId.of(ID);

    private AppTimeZone() {
    }

    public static void apply() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
