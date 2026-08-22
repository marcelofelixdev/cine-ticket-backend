package com.app.cineticket.dto.request;

import java.math.BigDecimal;

public record PaymentEventDTO (

        Long ticketId,
        String cartaoToken,
        BigDecimal valor

) {}
