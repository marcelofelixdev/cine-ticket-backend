package com.app.cineticket.security;

import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.RefreshTokenRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de autenticação do frontend: login, refresh com rotação, detecção de reuso e logout.
 */
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String EMAIL = "auth-flow@teste.com";
    private static final String PASSWORD = "senha-de-teste-123";
    /** O login tem limite de 5 por minuto por IP: cada login usa um IP diferente. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanUp();
        Role role = com.app.cineticket.support.TestRoles.ensure(roleRepository, "ROLE_USER");
        User user = new User();
        user.setNome("Fluxo de Auth");
        user.setEmail(EMAIL);
        user.setSenha(passwordEncoder.encode(PASSWORD));
        user.setRoles(new HashSet<>(Set.of(role)));
        userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void loginDevolveAccessTokenCurtoERefreshEmCookieHttpOnly() throws Exception {
        mockMvc.perform(login())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString(RefreshTokenCookies.NAME + "="),
                        containsString("HttpOnly"),
                        containsString("Path=/api/v1/auth"),
                        containsString("SameSite=Lax"))));
    }

    @Test
    void refreshTrocaORefreshTokenEDevolveAccessTokenValido() throws Exception {
        String original = refreshCookieOf(mockMvc.perform(login()).andReturn());

        MvcResult refreshed = mockMvc.perform(refresh(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String rotated = refreshCookieOf(refreshed);
        String accessToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");

        assertNotEquals(original, rotated);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void reusoDeRefreshTokenJaTrocadoRevogaASessaoInteira() throws Exception {
        String original = refreshCookieOf(mockMvc.perform(login()).andReturn());
        String rotated = refreshCookieOf(mockMvc.perform(refresh(original)).andReturn());

        mockMvc.perform(refresh(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));

        // O token novo, de quem fez o refresh legítimo, também deixa de valer.
        mockMvc.perform(refresh(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaASessaoEApagaOCookie() throws Exception {
        String refreshToken = refreshCookieOf(mockMvc.perform(login()).andReturn());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshTokenCookies.NAME, refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        mockMvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSemCookieRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshDeOrigemNaoPermitidaRetorna403() throws Exception {
        String refreshToken = refreshCookieOf(mockMvc.perform(login()).andReturn());

        mockMvc.perform(refresh(refreshToken).header("Origin", "https://site-malicioso.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void senhaErradaRetorna401() throws Exception {
        mockMvc.perform(withNewIp(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"senha-errada-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder login() {
        return withNewIp(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + PASSWORD + "\"}");
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) {
        return post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookies.NAME, refreshToken));
    }

    private static MockHttpServletRequestBuilder withNewIp(MockHttpServletRequestBuilder request) {
        int n = NEXT_IP.getAndIncrement();
        return request.with(r -> {
            r.setRemoteAddr("10.0." + (n / 250) + "." + (n % 250 + 1));
            return r;
        });
    }

    private static String refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertNotNull(cookie, "resposta sem cookie de refresh");
        assertEquals(true, cookie.isHttpOnly());
        return cookie.getValue();
    }
}
