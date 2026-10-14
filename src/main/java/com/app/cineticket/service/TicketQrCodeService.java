package com.app.cineticket.service;

import com.app.cineticket.exception.InvalidQrCodeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Conteúdo do QR Code do ingresso: {@code CT1.<ticketId>.<assinatura>}.
 * <p>
 * A assinatura é um HMAC-SHA256 (truncado em 128 bits, em Base64 URL) do prefixo e do ID,
 * com um segredo que só o servidor conhece. Sem o segredo não dá para forjar um QR para
 * outro ingresso, e o QR não carrega nenhum dado pessoal. "CT1" é a versão do formato.
 */
@Service
public class TicketQrCodeService {

    private static final String VERSION = "CT1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 16;
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKeySpec key;

    public TicketQrCodeService(@Value("${app.tickets.qr-secret:}") String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("QR_SECRET deve ter pelo menos " + MIN_SECRET_LENGTH + " caracteres.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String payloadFor(long ticketId) {
        String data = VERSION + "." + ticketId;
        return data + "." + sign(data);
    }

    /** @return o ID do ingresso, se o QR for autêntico */
    public long verify(String payload) {
        if (payload == null) {
            throw invalid();
        }
        String[] parts = payload.trim().split("\\.");
        if (parts.length != 3 || !VERSION.equals(parts[0]) || !parts[1].matches("\\d{1,18}")) {
            throw invalid();
        }
        byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] received = parts[2].getBytes(StandardCharsets.US_ASCII);
        // Comparação em tempo constante: não revela por timing quantos caracteres batem.
        if (!MessageDigest.isEqual(expected, received)) {
            throw invalid();
        }
        return Long.parseLong(parts[1]);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] full = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o QR Code", e);
        }
    }

    private static InvalidQrCodeException invalid() {
        return new InvalidQrCodeException("QR Code inválido ou adulterado.");
    }
}
