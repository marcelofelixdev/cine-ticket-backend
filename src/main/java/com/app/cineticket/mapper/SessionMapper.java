package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.dto.request.SessionRequestDTO;
import com.app.cineticket.dto.response.SessionResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "movie", ignore = true)
    @Mapping(target = "room", ignore = true)
    Session toEntity(SessionRequestDTO requestDTO);

    @Mapping(source = "movie.titulo", target = "movieTitulo")
    @Mapping(source = "room.nome", target = "roomNome")
    @Mapping(source = "room.cinema.nome", target = "cinemaNome")
    SessionResponseDTO toResponseDTO(Session entity);
}
