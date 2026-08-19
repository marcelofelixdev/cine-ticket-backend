package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.dto.request.CinemaRequestDTO;
import com.app.cineticket.dto.response.CinemaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CinemaMapper {

    @Mapping(target = "id", ignore = true)
    Cinema toEntity(CinemaRequestDTO requestDTO);

    CinemaResponseDTO toResponseDTO(Cinema entity);
}
