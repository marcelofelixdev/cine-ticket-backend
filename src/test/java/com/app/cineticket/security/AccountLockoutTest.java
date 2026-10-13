package com.app.cineticket.security;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.RefreshTokenRepository;
import com.app.cineticket.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloqueio por conta após senhas erradas seguidas (item 16).
 * Cada requisição usa um IP diferente para não esbarrar no limite por IP.
 */
class AccountLockoutTest extends AbstractIntegrationTest {

    private static final String EMAIL = "lockout@teste.com";
    private static final String PASSWORD = "senha-correta-123";
    private static final String WRONG = "senha-errada-123";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanUp();
        User user = new User();
        user.setNome("Lockout");
        user.setEmail(EMAIL);
        user.setSenha(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void bloqueiaAContaAposCincoSenhasErradasMesmoComASenhaCerta() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(WRONG).andExpect(status().isUnauthorized());
        }

        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        "E-mail ou senha inválidos. Após várias tentativas, o acesso fica bloqueado por alguns minutos."));

        assertNotNull(currentUser().getLockedUntil());
    }

    @Test
    void liberaOLoginQuandoOBloqueioVence() throws Exception {
        User user = currentUser();
        user.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        userRepository.save(user);

        login(PASSWORD).andExpect(status().isOk());

        assertNull(currentUser().getLockedUntil());
    }

    @Test
    void loginCertoZeraOContador() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(WRONG).andExpect(status().isUnauthorized());
        }
        login(PASSWORD).andExpect(status().isOk());
        assertEquals(0, currentUser().getFailedLoginAttempts());

        for (int i = 0; i < 4; i++) {
            login(WRONG).andExpect(status().isUnauthorized());
        }
        login(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void emailInexistenteNaoQuebraNemRevelaNada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@teste.com\",\"senha\":\"" + WRONG + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    private ResultActions login(String senha) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(r -> { r.setRemoteAddr(nextIp()); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + senha + "\"}"));
    }

    private User currentUser() {
        return userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    private static String nextIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.1." + (n / 250) + "." + (n % 250 + 1);
    }
}
