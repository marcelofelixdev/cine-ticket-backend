package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "seat", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "paymentIntentId", ignore = true)
    @Mapping(target = "refunds", ignore = true)
    Ticket toEntity(TicketRequestDTO requestDTO);

    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "session.horarioInicio", target = "horarioSessao")
    @Mapping(source = "seat.id", target = "seatId")
    @Mapping(source = "session.movie.titulo", target = "filmeTitulo")
    @Mapping(source = "session.room.nome", target = "salaNome")
    @Mapping(target = "cadeiraAssento", expression = "java(entity.getSeat().getFila() + \"-\" + entity.getSeat().getNumero())")
    @Mapping(target = "reembolso", expression = "java(refundStatus(entity))")
    TicketResponseDTO toResponseDTO(Ticket entity);

    /** Reembolso do pagamento que pertence ao ingresso (ignora estornos de pagamentos duplicados). */
    default String refundStatus(Ticket entity) {
        if (entity.getRefunds() == null || entity.getRefunds().isEmpty()) {
            return null;
        }
        return entity.getRefunds().stream()
                .filter(r -> entity.getPaymentIntentId() == null
                        || entity.getPaymentIntentId().equals(r.getPaymentIntentId()))
                .findFirst()
                .map(r -> r.getStatus().name())
                .orElse(null);
    }
}
