package com.app.cineticket.dto.response;

public record TicketResponseDTO (
    Long id,
    String status,
    String filmeTitulo,
    String salaNome,
    String cadeiraAssento
){}
