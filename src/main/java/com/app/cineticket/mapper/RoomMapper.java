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
    @Mapping(target = "cadeirasPorFila", ignore = true) // definidos e validados no RoomService
    @Mapping(target = "corredores", ignore = true)
    Room toEntity(RoomRequestDTO requestDTO);

    @Mapping(source = "cinema.id", target = "cinemaId")
    @Mapping(source = "cinema.nome", target = "cinemaNome")
    RoomResponseDTO toResponseDTO(Room entity);

}
