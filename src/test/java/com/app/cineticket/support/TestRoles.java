package com.app.cineticket.support;

import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.repository.RoleRepository;

/**
 * Papéis para os testes de integração. Alguns testes apagam tb_role inteira na limpeza,
 * então não dá para contar com os papéis inseridos pelas migrações: busca ou recria.
 */
public final class TestRoles {

    private TestRoles() {
    }

    public static Role ensure(RoleRepository roleRepository, String nome) {
        return roleRepository.findByNome(nome).orElseGet(() -> {
            Role role = new Role();
            role.setNome(nome);
            return roleRepository.save(role);
        });
    }
}
