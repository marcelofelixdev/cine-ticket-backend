package com.app.cineticket.mapper;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.UserRequestDTO;
import com.app.cineticket.dto.response.UserResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRequestDTO requestDTO);

    @Mapping(target = "roles", expression = "java(mapRolesToStrings(entity.getRoles()))")
    UserResponseDTO toResponseDTO(User entity);

    default Set<String> mapRolesToStrings(Set<Role> roles) {
        if (roles == null) return null;
        return roles.stream().map(Role::getNome).collect(Collectors.toSet());
    }
}