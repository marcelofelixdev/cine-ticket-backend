package com.app.cineticket.controller;

import com.app.cineticket.dto.request.UserRequestDTO;
import com.app.cineticket.dto.response.UserResponseDTO;
import com.app.cineticket.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@RequestBody @Valid UserRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(requestDTO));
    }
}
