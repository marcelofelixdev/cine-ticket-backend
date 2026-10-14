package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionRequestDTO (

        @NotNull(message = "O horário é obrigatório")
        @Future(message = "O horário da sessão deve ser no futuro")
        LocalDateTime horarioInicio,

        @NotNull(message = "O valor base é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 8, fraction = 2)
        BigDecimal valorBase,

        @Positive(message = "O ID do filme deve ser positivo")
        long movieId,

        @Positive(message = "O ID da sala deve ser positivo")
        long roomId,

        /** 2D (padrão) ou 3D. */
        @jakarta.validation.constraints.Pattern(regexp = "2D|3D", message = "Formato deve ser 2D ou 3D")
        String formato,

        /** DUBLADO, LEGENDADO (padrão) ou NACIONAL. */
        com.app.cineticket.domain.enums.SessionAudio audio
) {}
