package com.app.cineticket.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTimeZoneTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void aplicaOFusoDeBrasiliaComoPadraoDaJvm() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        AppTimeZone.apply();

        assertEquals(ZoneId.of("America/Sao_Paulo"), ZoneId.systemDefault());
    }
}
