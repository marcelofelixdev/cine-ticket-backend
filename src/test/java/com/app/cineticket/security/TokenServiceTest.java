package com.app.cineticket.security;

import com.app.cineticket.domain.entity.User;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private final TokenService tokenService =
            new TokenService("test_secret_with_more_than_32_characters");

    @Test
    void deveEmitirEValidarTokenComAudienceEExpiracao() {
        var user = new User();
        user.setEmail("user@example.com");

        String token = tokenService.generateToken(user);
        var decoded = JWT.decode(token);

        assertEquals("user@example.com", tokenService.validateToken(token));
        assertTrue(decoded.getAudience().contains("cine-ticket-web"));
        long lifetimeMinutes = Duration.between(Instant.now(), decoded.getExpiresAtAsInstant()).toMinutes();
        assertTrue(lifetimeMinutes >= 118 && lifetimeMinutes <= 120);
    }

    @Test
    void deveRejeitarTokenInvalido() {
        assertEquals("", tokenService.validateToken("not-a-jwt"));
    }
}
