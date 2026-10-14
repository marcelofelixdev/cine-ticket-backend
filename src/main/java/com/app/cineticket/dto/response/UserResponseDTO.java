package com.app.cineticket.dto.response;

import java.util.Set;

public record UserResponseDTO (
        Long id,
        String nome,
        String email,
        Set<String> roles,
        java.time.LocalDate dataNascimento,
        /** Cinema do gerente ou funcionário; null para clientes e admins. */
        Long cinemaId
) {}
