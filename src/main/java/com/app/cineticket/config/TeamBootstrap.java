package com.app.cineticket.config;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.CinemaRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Cria a equipe de exemplo ao subir a aplicação: um gerente (ROLE_MANAGER) e um funcionário
 * (ROLE_STAFF). Ligado nos perfis dev e demo; desligado em produção por padrão.
 * <p>
 * Os dois pertencem ao primeiro cinema cadastrado. Se ainda não houver cinema, ficam sem
 * vínculo e são vinculados no próximo boot (ou pelo demo, que cria os cinemas depois).
 */
@Slf4j
@Component
public class TeamBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final Account staff;
    private final Account manager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CinemaRepository cinemaRepository;
    private final PasswordEncoder passwordEncoder;

    public TeamBootstrap(@Value("${app.bootstrap.staff.enabled:false}") boolean staffEnabled,
                         @Value("${app.bootstrap.staff.email:}") String staffEmail,
                         @Value("${app.bootstrap.staff.password:}") String staffPassword,
                         @Value("${app.bootstrap.manager.enabled:false}") boolean managerEnabled,
                         @Value("${app.bootstrap.manager.email:}") String managerEmail,
                         @Value("${app.bootstrap.manager.password:}") String managerPassword,
                         UserRepository userRepository,
                         RoleRepository roleRepository,
                         CinemaRepository cinemaRepository,
                         PasswordEncoder passwordEncoder) {
        this.staff = new Account(staffEnabled, staffEmail, staffPassword, "ROLE_STAFF", "Funcionário", "STAFF");
        this.manager = new Account(managerEnabled, managerEmail, managerPassword, "ROLE_MANAGER", "Gerente", "MANAGER");
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.cinemaRepository = cinemaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<Cinema> firstCinema = cinemaRepository.findFirstByOrderByIdAsc();
        for (Account account : List.of(manager, staff)) {
            if (account.enabled()) {
                ensure(account, firstCinema.orElse(null));
            }
        }
    }

    private void ensure(Account account, Cinema cinema) {
        if (account.email() == null || !account.email().contains("@")
                || account.password() == null || account.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("[BOOTSTRAP] " + account.envPrefix() + "_EMAIL/" + account.envPrefix()
                    + "_PASSWORD inválidos (senha com 12+ caracteres).");
        }
        String normalizedEmail = account.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getCinema() == null && cinema != null) {
                user.setCinema(cinema);
                log.info("[BOOTSTRAP] {} {} vinculado ao cinema {}.", account.nome(), normalizedEmail, cinema.getId());
            }
            return;
        }
        Role role = roleRepository.findByNome(account.role())
                .orElseThrow(() -> new IllegalStateException("[BOOTSTRAP] Role " + account.role() + " não encontrada."));
        User user = new User();
        user.setNome(account.nome());
        user.setEmail(normalizedEmail);
        user.setSenha(passwordEncoder.encode(account.password()));
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setCinema(cinema);
        userRepository.save(user);
        if (cinema == null) {
            log.warn("[BOOTSTRAP] {} {} criado sem cinema (nenhum cinema cadastrado ainda).", account.nome(), normalizedEmail);
        } else {
            log.info("[BOOTSTRAP] {} {} criado no cinema {}.", account.nome(), normalizedEmail, cinema.getId());
        }
    }

    private record Account(boolean enabled, String email, String password, String role, String nome, String envPrefix) {
    }
}
