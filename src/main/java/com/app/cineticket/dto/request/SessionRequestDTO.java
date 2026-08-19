package com.app.cineticket.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionRequestDTO (

        @NotNull(message = "O horário é obrigatório")
        @Future(message = "O horário da sessão deve ser no futuro")
        LocalDateTime horarioInicio,

        @NotNull(message = "O valor base é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        BigDecimal valorBase,

        @NotNull(message = "O ID do filme é obrigatório")
        long movieId,

        @NotNull(message = "O ID da sala é obrigatório")
        long roomId
) {}
