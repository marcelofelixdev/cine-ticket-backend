package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TeamRole;
import com.app.cineticket.dto.request.StaffRequestDTO;
import com.app.cineticket.dto.request.TeamMemberRequestDTO;
import com.app.cineticket.dto.response.TeamMemberResponseDTO;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Equipe dos cinemas: o admin cadastra gerentes e funcionários em qualquer cinema;
 * o gerente cadastra funcionários só no próprio cinema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final List<String> TEAM_ROLES = List.of(TeamRole.MANAGER.roleName(), TeamRole.STAFF.roleName());

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CinemaRepository cinemaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TeamMemberResponseDTO createByAdmin(TeamMemberRequestDTO request) {
        Cinema cinema = cinemaRepository.findById(request.cinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema não encontrado. ID: " + request.cinemaId()));
        return create(request.nome(), request.email(), request.senha(), request.papel(), cinema);
    }

    /** O funcionário é criado no cinema do gerente; o pedido não escolhe papel nem cinema. */
    @Transactional
    public TeamMemberResponseDTO createByManager(StaffRequestDTO request, User manager) {
        Cinema cinema = cinemaRepository.findById(cinemaIdOf(manager))
                .orElseThrow(() -> new ForbiddenException("Gerente sem cinema vinculado."));
        return create(request.nome(), request.email(), request.senha(), TeamRole.STAFF, cinema);
    }

    /** Gerentes e funcionários; com cinemaId, só os daquele cinema. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponseDTO> listForAdmin(Long cinemaId) {
        List<User> team = cinemaId == null
                ? userRepository.findTeam(TEAM_ROLES)
                : userRepository.findTeamByCinema(TEAM_ROLES, cinemaId);
        return team.stream().map(TeamService::toResponse).toList();
    }

    /** Funcionários do cinema do gerente. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponseDTO> listStaffOf(User manager) {
        return userRepository.findTeamByCinema(List.of(TeamRole.STAFF.roleName()), cinemaIdOf(manager)).stream()
                .map(TeamService::toResponse)
                .toList();
    }

    private TeamMemberResponseDTO create(String nome, String email, String senha, TeamRole papel, Cinema cinema) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("E-mail já cadastrado.");
        }
        Role role = roleRepository.findByNome(papel.roleName())
                .orElseThrow(() -> new IllegalStateException("Role " + papel.roleName() + " não encontrada no sistema."));

        User user = new User();
        user.setNome(nome.trim());
        user.setEmail(normalizedEmail);
        user.setSenha(passwordEncoder.encode(senha));
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setCinema(cinema);
        User saved = userRepository.save(user);
        log.info("[EQUIPE] {} {} criado no cinema {}.", papel, normalizedEmail, cinema.getId());
        return toResponse(saved);
    }

    private static Long cinemaIdOf(User manager) {
        if (manager.getCinema() == null) {
            throw new ForbiddenException("Gerente sem cinema vinculado.");
        }
        return manager.getCinema().getId();
    }

    private static TeamMemberResponseDTO toResponse(User user) {
        TeamRole papel = user.hasRole(TeamRole.MANAGER.roleName()) ? TeamRole.MANAGER : TeamRole.STAFF;
        Cinema cinema = user.getCinema();
        return new TeamMemberResponseDTO(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                papel,
                cinema == null ? null : cinema.getId(),
                cinema == null ? null : cinema.getNome());
    }
}
