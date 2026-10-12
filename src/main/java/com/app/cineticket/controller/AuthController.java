package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.LoginRequestDTO;
import com.app.cineticket.dto.response.LoginResponseDTO;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.security.LoginAttemptService;
import com.app.cineticket.security.RefreshTokenCookies;
import com.app.cineticket.security.RefreshTokenService;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Autenticação para o frontend:
 * <ul>
 *     <li>{@code POST /login}: devolve o access token (curto) no corpo e o refresh token
 *     em cookie HttpOnly.</li>
 *     <li>{@code POST /refresh}: troca o refresh token do cookie por um novo par (rotação).</li>
 *     <li>{@code POST /logout}: revoga a sessão e apaga o cookie.</li>
 * </ul>
 * O frontend guarda o access token só em memória; ao recarregar a página, chama /refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final RateLimitService rateLimitService;
    private final LoginAttemptService loginAttemptService;
    private final List<String> allowedOrigins;

    public AuthController(AuthenticationManager authenticationManager,
                          TokenService tokenService,
                          RefreshTokenService refreshTokenService,
                          RefreshTokenCookies refreshTokenCookies,
                          RateLimitService rateLimitService,
                          LoginAttemptService loginAttemptService,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.rateLimitService = rateLimitService;
        this.loginAttemptService = loginAttemptService;
        this.allowedOrigins = allowedOrigins;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO requestDTO,
                                                  HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!rateLimitService.getUserBucket("login:" + ip).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente novamente mais tarde.");
        }

        String email = requestDTO.email().trim().toLowerCase(Locale.ROOT);
        var usernamePassword = new UsernamePasswordAuthenticationToken(email, requestDTO.senha());
        org.springframework.security.core.Authentication auth;
        try {
            auth = authenticationManager.authenticate(usernamePassword);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw e;
        }
        User user = (User) auth.getPrincipal();
        loginAttemptService.recordSuccess(user);

        return tokenResponse(user, refreshTokenService.issue(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        rejectForeignOrigin(request);
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return tokenResponse(rotation.user(), rotation.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        rejectForeignOrigin(request);
        refreshTokenService.revokeFamilyOf(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResponseDTO> tokenResponse(User user, RefreshTokenService.IssuedRefreshToken refresh) {
        String accessToken = tokenService.generateToken(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(refresh.value(), refresh.ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new LoginResponseDTO(accessToken, tokenService.getAccessTokenTtl().toSeconds()));
    }

    /**
     * Proteção contra CSRF nas rotas que usam o cookie: navegadores sempre enviam o
     * cabeçalho Origin em POST cross-site, então uma origem fora da lista do CORS é recusada.
     * Clientes sem navegador (curl, Postman) não enviam Origin e continuam funcionando.
     */
    private void rejectForeignOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new ForbiddenException("Origem não permitida.");
        }
    }
}
