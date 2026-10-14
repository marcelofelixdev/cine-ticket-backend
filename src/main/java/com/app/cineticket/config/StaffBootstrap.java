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
 * Cria um funcionário de entrada (ROLE_STAFF) ao subir a aplicação, para validar ingressos.
 * Ligado nos perfis dev e demo; desligado em produção por padrão.
 * O funcionário recebe só ROLE_STAFF: valida ingressos e nada da área administrativa.
 */
@Slf4j
@Component
public class StaffBootstrap implements ApplicationRunner {

    static final String ROLE_STAFF = "ROLE_STAFF";
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final boolean enabled;
    private final String email;
    private final String password;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffBootstrap(@Value("${app.bootstrap.staff.enabled:false}") boolean enabled,
                          @Value("${app.bootstrap.staff.email:}") String email,
                          @Value("${app.bootstrap.staff.password:}") String password,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (email == null || !email.contains("@") || password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("[BOOTSTRAP] STAFF_EMAIL/STAFF_PASSWORD inválidos (senha com 12+ caracteres).");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            log.info("[BOOTSTRAP] Funcionário {} já existe. Nada a fazer.", normalizedEmail);
            return;
        }
        Role staffRole = roleRepository.findByNome(ROLE_STAFF)
                .orElseThrow(() -> new IllegalStateException("[BOOTSTRAP] Role ROLE_STAFF não encontrada (V11 aplicada?)."));
        User staff = new User();
        staff.setNome("Funcionário da entrada");
        staff.setEmail(normalizedEmail);
        staff.setSenha(passwordEncoder.encode(password));
        staff.setRoles(new HashSet<>(Set.of(staffRole)));
        userRepository.save(staff);
        log.info("[BOOTSTRAP] Funcionário de entrada {} criado.", normalizedEmail);
    }
}
