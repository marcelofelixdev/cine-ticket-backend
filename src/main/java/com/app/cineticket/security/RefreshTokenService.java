package com.app.cineticket.security;

import com.app.cineticket.domain.entity.RefreshToken;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.exception.UnauthorizedException;
import com.app.cineticket.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens opacos e rotativos.
 * <ul>
 *     <li>Cada uso troca o token por um novo (rotação) e invalida o anterior.</li>
 *     <li>Se um token já trocado for usado de novo, alguém pode tê-lo copiado:
 *     a família inteira é revogada e o usuário precisa fazer login de novo.</li>
 *     <li>Só o hash SHA-256 vai para o banco.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_SESSION = "Sessão expirada ou inválida. Faça login novamente.";

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.auth.refresh-token.ttl:7d}") Duration ttl) {
        this.repository = repository;
        this.ttl = ttl;
    }

    /** Valor que vai para o cookie e por quanto tempo ele vale. */
    public record IssuedRefreshToken(String value, Duration ttl) {
    }

    /** Resultado da rotação: o dono do token e o novo refresh token. */
    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }

    /** Novo login: abre uma nova família de tokens. */
    @Transactional
    public IssuedRefreshToken issue(User user) {
        return issueInFamily(user, UUID.randomUUID());
    }

    /**
     * Troca um refresh token válido por um novo da mesma família.
     * O {@code noRollbackFor} garante que a revogação da família por reuso seja gravada
     * mesmo com a exceção que responde 401.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException(INVALID_SESSION);
        }

        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID_SESSION));
        LocalDateTime now = LocalDateTime.now();

        if (current.getRevokedAt() != null) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("[AUTH] Reuso de refresh token detectado para o usuário {}. {} token(s) da família revogado(s).",
                    current.getUser().getId(), revoked);
            throw new UnauthorizedException(INVALID_SESSION);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException(INVALID_SESSION);
        }

        current.setRevokedAt(now);
        IssuedRefreshToken next = issueInFamily(current.getUser(), current.getFamilyId());
        return new Rotation(current.getUser(), next);
    }

    /** Logout: revoga todos os tokens da família do token informado. Token ausente ou desconhecido é ignorado. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    /** Limpeza diária dos tokens vencidos há mais de um dia. */
    @Scheduled(cron = "${app.auth.refresh-token.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.info("[AUTH] {} refresh token(s) vencido(s) removido(s).", deleted);
        }
    }

    private IssuedRefreshToken issueInFamily(User user, UUID familyId) {
        String rawToken = newRawToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        repository.save(token);
        return new IssuedRefreshToken(rawToken, ttl);
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
