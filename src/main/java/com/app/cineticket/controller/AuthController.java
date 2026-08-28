package com.app.cineticket.controller;


import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.LoginRequestDTO;
import com.app.cineticket.dto.response.LoginResponseDTO;
import com.app.cineticket.security.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.app.cineticket.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RateLimitService rateLimitService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO requestDTO, HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        if (!rateLimitService.getUserBucket("login:" + ip).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de login. Tente novamente mais tarde.");
        }

        var usernamePassword = new UsernamePasswordAuthenticationToken(
                requestDTO.email().trim().toLowerCase(java.util.Locale.ROOT), requestDTO.senha());

        var auth = authenticationManager.authenticate(usernamePassword);

        var token = tokenService.generateToken((User) auth.getPrincipal());

        return ResponseEntity.ok(new LoginResponseDTO(token));
    }
}
