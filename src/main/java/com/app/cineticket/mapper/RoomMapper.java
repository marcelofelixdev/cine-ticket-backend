package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.dto.response.RoomResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cinema", ignore = true)
    Room toEntity(RoomRequestDTO requestDTO);

    @Mapping(source = "cinema.nome", target = "cinemaNome")
    RoomResponseDTO toResponseDTO(Room entity);
}
