package com.app.cineticket.domain.enums;

/** Papéis da equipe de um cinema, criados pelo admin (gerente ou funcionário) ou pelo gerente (funcionário). */
public enum TeamRole {
    MANAGER("ROLE_MANAGER"),
    STAFF("ROLE_STAFF");

    private final String roleName;

    TeamRole(String roleName) {
        this.roleName = roleName;
    }

    public String roleName() {
        return roleName;
    }
}
