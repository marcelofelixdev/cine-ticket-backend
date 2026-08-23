package com.app.cineticket.dto.request;

import java.math.BigDecimal;

public record RefundEventDTO(
        Long ticketId,
        BigDecimal valorEstorno
) {}
