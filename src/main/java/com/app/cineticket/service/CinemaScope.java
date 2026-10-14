package com.app.cineticket.service;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.exception.ForbiddenException;

/** Regras de escopo da equipe de cinema (gerente e funcionário só agem no próprio cinema). */
public final class CinemaScope {

    private CinemaScope() {
    }

    /** Cinema do gerente ou funcionário; sem cinema vinculado, 403. */
    public static Long cinemaIdOf(User user) {
        if (user.getCinema() == null) {
            throw new ForbiddenException("Usuário sem cinema vinculado.");
        }
        return user.getCinema().getId();
    }

    /** 403 com a mensagem informada se o recurso for de outro cinema. */
    public static void requireSameCinema(User user, Long cinemaId, String message) {
        if (!cinemaIdOf(user).equals(cinemaId)) {
            throw new ForbiddenException(message);
        }
    }
}
