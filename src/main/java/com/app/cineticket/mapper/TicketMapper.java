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
    Ticket toEntity(TicketRequestDTO requestDTO);

    @Mapping(source = "session.movie.titulo", target = "filmeTitulo")
    @Mapping(source = "session.room.nome", target = "salaNome")
    @Mapping(target = "cadeiraAssento", expression = "java(entity.getSeat().getFila() + \"-\" + entity.getSeat().getNumero())")
    TicketResponseDTO toResponseDTO(Ticket entity);
}
