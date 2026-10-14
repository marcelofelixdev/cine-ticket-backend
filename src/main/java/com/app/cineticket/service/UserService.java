package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.UserRequestDTO;
import com.app.cineticket.dto.response.UserResponseDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.mapper.UserMapper;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

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
            throw new ConflictException("E-mail já cadastrado.");
        }

        User user = userMapper.toEntity(requestDTO);
        user.setEmail(normalizedEmail);

        user.setSenha(passwordEncoder.encode(requestDTO.senha()));

        Set<Role> roles = new HashSet<>();
        Role defaultRole = roleRepository.findByNome("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_USER não encontrada no sistema."));
        roles.add(defaultRole);
        user.setRoles(roles);

        return userMapper.toResponseDTO(userRepository.save(user));

    }

    /** Atualiza nome e/ou data de nascimento do próprio usuário (campos nulos ficam como estão). */
    @Transactional
    public UserResponseDTO updateMe(User loggedUser, com.app.cineticket.dto.request.UserUpdateDTO update) {
        User user = userRepository.findById(loggedUser.getId())
                .orElseThrow(() -> new com.app.cineticket.exception.ResourceNotFoundException("Usuário não encontrado."));
        if (update.nome() != null && !update.nome().isBlank()) {
            user.setNome(update.nome().trim());
        }
        if (update.dataNascimento() != null) {
            user.setDataNascimento(update.dataNascimento());
        }
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    /** Dados do usuário logado. */
    public UserResponseDTO me(User loggedUser) {
        return userMapper.toResponseDTO(loggedUser);
    }

}
