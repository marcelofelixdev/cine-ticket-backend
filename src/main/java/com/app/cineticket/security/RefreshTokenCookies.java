package com.app.cineticket.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie do refresh token: HttpOnly (o JavaScript não lê, protege contra XSS) e
 * restrito ao caminho /api/v1/auth (só vai para refresh e logout).
 * <p>
 * Em dev: Secure=false e SameSite=Lax (frontend e API em localhost).
 * Em produção: Secure=true e SameSite=None, porque o frontend fica em outro domínio.
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "cineticket_refresh";
    static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookies(@Value("${app.auth.refresh-cookie.secure:true}") boolean secure,
                               @Value("${app.auth.refresh-cookie.same-site:Strict}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String value, Duration maxAge) {
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH);
    }
}
