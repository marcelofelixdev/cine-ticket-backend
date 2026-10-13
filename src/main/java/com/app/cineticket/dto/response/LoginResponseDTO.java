package com.app.cineticket.dto.response;

/**
 * @param token     access token JWT (enviar em Authorization: Bearer)
 * @param expiresIn validade do access token, em segundos
 */
public record LoginResponseDTO(String token, long expiresIn) {}
