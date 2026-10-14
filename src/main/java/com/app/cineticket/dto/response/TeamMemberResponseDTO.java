package com.app.cineticket.dto.response;

import com.app.cineticket.domain.enums.TeamRole;

public record TeamMemberResponseDTO(
        Long id,
        String nome,
        String email,
        TeamRole papel,
        Long cinemaId,
        String cinemaNome
) {}
