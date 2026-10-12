package com.app.cineticket.security;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Bloqueio temporário por conta contra força bruta (complementa o limite por IP).
 * <p>
 * Depois de {@code maxAttempts} senhas erradas seguidas, a conta fica bloqueada por
 * {@code lockDuration}. Um login certo zera o contador. Enquanto bloqueada, até a senha
 * certa é recusada, com a mesma mensagem genérica de credencial inválida.
 */
@Slf4j
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(UserRepository userRepository,
                               @Value("${app.auth.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.auth.login.lock-duration:15m}") Duration lockDuration) {
        this.userRepository = userRepository;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    /** Senha errada para um e-mail. E-mail inexistente é ignorado (não há conta a proteger). */
    @Transactional
    public void recordFailure(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            if (user.isLockedAt(now)) {
                return;
            }
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= maxAttempts) {
                user.setLockedUntil(now.plus(lockDuration));
                user.setFailedLoginAttempts(0);
                log.warn("[AUTH] Conta {} bloqueada por {} min após {} tentativas de login falhas.",
                        user.getId(), lockDuration.toMinutes(), attempts);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            userRepository.save(user);
        });
    }

    /** Login certo: zera o contador e remove um bloqueio já vencido. */
    @Transactional
    public void recordSuccess(User user) {
        if (user.getFailedLoginAttempts() == 0 && user.getLockedUntil() == null) {
            return;
        }
        userRepository.findById(user.getId()).ifPresent(managed -> {
            managed.setFailedLoginAttempts(0);
            managed.setLockedUntil(null);
            userRepository.save(managed);
        });
    }
}
