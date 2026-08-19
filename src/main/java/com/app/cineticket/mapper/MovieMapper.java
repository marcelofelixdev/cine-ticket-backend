package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.request.MovieRequestDTO;
import com.app.cineticket.dto.response.MovieResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MovieMapper {

    @Mapping(target = "id", ignore = true)
    Movie toEntity(MovieRequestDTO requestDTO);

    MovieResponseDTO toResponseDTO(Movie entity);
}