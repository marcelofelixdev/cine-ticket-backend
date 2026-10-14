package com.app.cineticket.demo;

import com.app.cineticket.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoRestrictionsTest {

    @Test
    void noDemoBloqueiaComMensagem() {
        DemoRestrictions restrictions = restrictionsWith("prod", "demo");

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> restrictions.requireNotDemo("Cancelar sessões"));
        assertEquals("Cancelar sessões fica desativado na demonstração pública.", error.getMessage());
    }

    @Test
    void foraDoDemoLibera() {
        DemoRestrictions restrictions = restrictionsWith("prod");

        assertDoesNotThrow(() -> restrictions.requireNotDemo("Cancelar sessões"));
    }

    private static DemoRestrictions restrictionsWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DemoRestrictions(environment);
    }
}
