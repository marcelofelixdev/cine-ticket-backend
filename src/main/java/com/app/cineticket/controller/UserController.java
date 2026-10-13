package com.app.cineticket.controller;

import com.app.cineticket.dto.request.UserRequestDTO;
import com.app.cineticket.dto.response.UserResponseDTO;
import com.app.cineticket.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final com.app.cineticket.service.RateLimitService rateLimitService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me() {
        var loggedUser = (com.app.cineticket.domain.entity.User)
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.me(loggedUser));
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(
            @RequestBody @Valid UserRequestDTO requestDTO,
            jakarta.servlet.http.HttpServletRequest request) {
        if (!rateLimitService.getUserBucket("registration:" + request.getRemoteAddr()).tryConsume(1)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de cadastro. Tente novamente mais tarde.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(requestDTO));
    }
}
