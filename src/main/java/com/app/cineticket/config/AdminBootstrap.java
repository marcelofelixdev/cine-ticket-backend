package com.app.cineticket.config;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Garante que exista um usuário administrador ao subir a aplicação.
 * <p>
 * Roda depois das migrações do Flyway. É idempotente:
 * <ul>
 *     <li>se o e-mail não existe, cria o usuário com ROLE_USER e ROLE_ADMIN;</li>
 *     <li>se existe sem ROLE_ADMIN, apenas adiciona a role (a senha não é alterada);</li>
 *     <li>se já é admin, não faz nada.</li>
 * </ul>
 * Ligado por padrão só no perfil dev. Em produção depende de ADMIN_BOOTSTRAP_ENABLED=true,
 * e então ADMIN_EMAIL e ADMIN_PASSWORD passam a ser obrigatórios.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String ROLE_USER = "ROLE_USER";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final int MAX_NAME_LENGTH = 150;

    private final boolean enabled;
    private final String email;
    private final String password;
    private final String name;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(@Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
                          @Value("${app.bootstrap.admin.email:}") String email,
                          @Value("${app.bootstrap.admin.password:}") String password,
                          @Value("${app.bootstrap.admin.name:Administrador}") String name,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.name = name;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("[BOOTSTRAP] Criação automática do admin desligada.");
            return;
        }
        validateConfiguration();

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Role adminRole = findRole(ROLE_ADMIN);

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresentOrElse(
                user -> promote(user, adminRole),
                () -> create(normalizedEmail, adminRole, findRole(ROLE_USER))
        );
    }

    private void create(String normalizedEmail, Role adminRole, Role userRole) {
        User admin = new User();
        admin.setNome(resolveName());
        admin.setEmail(normalizedEmail);
        admin.setSenha(passwordEncoder.encode(password));
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        roles.add(adminRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        log.info("[BOOTSTRAP] Admin {} criado.", normalizedEmail);
    }

    private void promote(User user, Role adminRole) {
        boolean alreadyAdmin = user.getRoles().stream()
                .anyMatch(role -> ROLE_ADMIN.equals(role.getNome()));
        if (alreadyAdmin) {
            log.info("[BOOTSTRAP] Admin {} já existe. Nada a fazer.", user.getEmail());
            return;
        }
        user.getRoles().add(adminRole);
        userRepository.save(user);
        log.info("[BOOTSTRAP] Usuário {} promovido a admin. A senha existente foi mantida.", user.getEmail());
    }

    private Role findRole(String roleName) {
        return roleRepository.findByNome(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "[BOOTSTRAP] Role " + roleName + " não encontrada. As migrações do Flyway foram aplicadas?"));
    }

    private void validateConfiguration() {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalStateException(
                    "[BOOTSTRAP] ADMIN_EMAIL ausente ou inválido. Defina a variável ou desligue ADMIN_BOOTSTRAP_ENABLED.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "[BOOTSTRAP] ADMIN_PASSWORD deve ter entre " + MIN_PASSWORD_LENGTH + " e "
                            + MAX_PASSWORD_LENGTH + " caracteres.");
        }
    }

    private String resolveName() {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "Administrador";
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
