package com.app.cineticket.service;

import com.app.cineticket.exception.InvalidQrCodeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketQrCodeServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres";

    private final TicketQrCodeService service = new TicketQrCodeService(SECRET);

    @Test
    void qrGeradoEValidadoDevolveOIngresso() {
        String qr = service.payloadFor(42L);

        assertTrue(qr.startsWith("CT1.42."));
        assertEquals(42L, service.verify(qr));
    }

    @Test
    void qrNaoCarregaDadoPessoal() {
        String qr = service.payloadFor(42L);

        assertFalse(qr.contains("@"));
        assertEquals(3, qr.split("\\.").length);
    }

    @Test
    void trocarOIdDoIngressoInvalidaAAssinatura() {
        String forged = service.payloadFor(42L).replace("CT1.42.", "CT1.43.");

        assertThrows(InvalidQrCodeException.class, () -> service.verify(forged));
    }

    @Test
    void qrAssinadoComOutroSegredoEInvalido() {
        String other = new TicketQrCodeService("outro-segredo-tambem-com-mais-de-32-caracteres").payloadFor(42L);

        assertThrows(InvalidQrCodeException.class, () -> service.verify(other));
    }

    @Test
    void formatoInvalidoERejeitado() {
        assertThrows(InvalidQrCodeException.class, () -> service.verify("CINETICKET-VALIDATION-ID:42-USER:a@b.com"));
        assertThrows(InvalidQrCodeException.class, () -> service.verify("CT1.abc.xyz"));
        assertThrows(InvalidQrCodeException.class, () -> service.verify(null));
    }

    @Test
    void segredoCurtoImpedeASubida() {
        assertThrows(IllegalStateException.class, () -> new TicketQrCodeService("curto"));
    }
}
