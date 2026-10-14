package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.dto.response.MovieResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MovieMapper {

    String YOUTUBE_WATCH = "https://www.youtube.com/watch?v=";

    @Mapping(target = "generos", expression = "java(splitGeneros(entity.getGeneros()))")
    @Mapping(target = "trailerUrl", expression = "java(trailerUrl(entity.getTrailerYoutubeKey()))")
    MovieResponseDTO toResponseDTO(Movie entity);

    // @Named tira estes métodos da escolha automática do MapStruct (senão trailerUrl seria
    // aplicado a qualquer campo String -> String).
    @Named("splitGeneros")
    default List<String> splitGeneros(String generos) {
        if (generos == null || generos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(generos.split(",")).map(String::trim).filter(g -> !g.isEmpty()).toList();
    }

    @Named("trailerUrl")
    default String trailerUrl(String key) {
        return key == null || key.isBlank() ? null : YOUTUBE_WATCH + key;
    }
}
