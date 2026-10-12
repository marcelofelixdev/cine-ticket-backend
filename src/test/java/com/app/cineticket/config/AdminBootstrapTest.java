package com.app.cineticket.config;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private static final String EMAIL = "Admin@Teste.com";
    private static final String PASSWORD = "senha-forte-123";

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        adminRole = role(2L, AdminBootstrap.ROLE_ADMIN);
        userRole = role(1L, AdminBootstrap.ROLE_USER);
    }

    @Test
    void naoFazNadaQuandoDesligado() {
        bootstrap(false, EMAIL, PASSWORD).run(null);

        verifyNoInteractions(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void criaAdminQuandoEmailNaoExiste() {
        when(roleRepository.findByNome(AdminBootstrap.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByNome(AdminBootstrap.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(userRepository.findByEmailIgnoreCase("admin@teste.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");

        bootstrap(true, EMAIL, PASSWORD).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("admin@teste.com", saved.getEmail());
        assertEquals("hash", saved.getSenha());
        assertEquals("Administrador", saved.getNome());
        assertEquals(Set.of(adminRole, userRole), saved.getRoles());
    }

    @Test
    void promoveUsuarioExistenteSemAlterarSenha() {
        User existing = user("hash-antigo", userRole);
        when(roleRepository.findByNome(AdminBootstrap.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmailIgnoreCase("admin@teste.com")).thenReturn(Optional.of(existing));

        bootstrap(true, EMAIL, PASSWORD).run(null);

        verify(userRepository).save(existing);
        assertTrue(existing.getRoles().contains(adminRole));
        assertEquals("hash-antigo", existing.getSenha());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void naoAlteraNadaQuandoJaEhAdmin() {
        User existing = user("hash-antigo", userRole, adminRole);
        when(roleRepository.findByNome(AdminBootstrap.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmailIgnoreCase("admin@teste.com")).thenReturn(Optional.of(existing));

        bootstrap(true, EMAIL, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void falhaComSenhaCurta() {
        AdminBootstrap bootstrap = bootstrap(true, EMAIL, "curta");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    void falhaSemEmail() {
        AdminBootstrap bootstrap = bootstrap(true, "  ", PASSWORD);

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    void falhaQuandoRoleAdminNaoExiste() {
        when(roleRepository.findByNome(AdminBootstrap.ROLE_ADMIN)).thenReturn(Optional.empty());

        AdminBootstrap bootstrap = bootstrap(true, EMAIL, PASSWORD);

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(userRepository, never()).save(any());
    }

    private AdminBootstrap bootstrap(boolean enabled, String email, String password) {
        return new AdminBootstrap(enabled, email, password, "", userRepository, roleRepository, passwordEncoder);
    }

    private static Role role(Long id, String nome) {
        Role role = new Role();
        role.setId(id);
        role.setNome(nome);
        return role;
    }

    private static User user(String senha, Role... roles) {
        User user = new User();
        user.setId(10L);
        user.setEmail("admin@teste.com");
        user.setSenha(senha);
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }
}
