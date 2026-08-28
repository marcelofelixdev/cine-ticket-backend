package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.UserRequestDTO;
import com.app.cineticket.dto.response.UserResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.mapper.UserMapper;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponseDTO create(UserRequestDTO requestDTO) {
        String normalizedEmail = requestDTO.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("Email já cadastrado");
        }

        User user = userMapper.toEntity(requestDTO);
        user.setEmail(normalizedEmail);

        user.setSenha(passwordEncoder.encode(requestDTO.senha()));

        Set<Role> roles = new HashSet<>();
        Role defaultRole = roleRepository.findByNome("ROLE_USER")
                .orElseThrow(() -> new BusinessException("Role ROLE_USER não encontrada no sistema."));
        roles.add(defaultRole);
        user.setRoles(roles);

        return userMapper.toResponseDTO(userRepository.save(user));

    }

    @Transactional
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

}
