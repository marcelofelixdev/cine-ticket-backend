package com.app.cineticket.dto.request;

import com.app.cineticket.domain.enums.TeamRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin cadastra um gerente ou funcionário em um cinema. */
public record TeamMemberRequestDTO(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 12, max = 72) String senha,
        @NotNull TeamRole papel,
        @NotNull Long cinemaId
) {}
