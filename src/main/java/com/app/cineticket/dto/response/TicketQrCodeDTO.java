package com.app.cineticket.dto.response;

/** Conteúdo do QR Code do ingresso, para o frontend desenhar o ingresso digital. */
public record TicketQrCodeDTO(Long ticketId, String qr) {}
