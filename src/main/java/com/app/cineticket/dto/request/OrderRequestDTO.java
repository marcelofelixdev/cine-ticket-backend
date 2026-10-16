package com.app.cineticket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Pedido da compra online: lugares de uma sessão, cada um com o tipo de ingresso. O limite de
 * lugares por pedido vem da configuração (o mapa da sala informa em maxLugaresPorPedido); o
 * {@code @Size} só barra pedidos absurdos antes de chegar à regra.
 */
public record OrderRequestDTO(
        @NotNull @Positive Long sessionId,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull BoxOfficeSaleRequestDTO.Item> itens
) {
}
