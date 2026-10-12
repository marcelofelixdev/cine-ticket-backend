package com.app.cineticket.security;

import com.app.cineticket.domain.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "cine-ticket-api";
    private static final String AUDIENCE = "cine-ticket-web";

    private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final String secret;
    /** Access token curto: se vazar, vale pouco tempo. A sessão longa fica no refresh token. */
    private final Duration accessTokenTtl;

    public TokenService(String secret) {
        this(secret, DEFAULT_ACCESS_TOKEN_TTL);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TokenService(@Value("${api.security.token.secret}") String secret,
                        @Value("${api.security.token.access-ttl:15m}") Duration accessTokenTtl) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET deve possuir pelo menos 32 caracteres");
        }
        this.secret = secret;
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withAudience(AUDIENCE)
                    .withSubject(user.getEmail())
                    .withIssuedAt(Instant.now())
                    .withJWTId(UUID.randomUUID().toString())
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Error ao gerar token JWT: " + exception);
        }
    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withAudience(AUDIENCE)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    private Instant genExpirationDate() {
        return Instant.now().plus(accessTokenTtl);
    }

}
