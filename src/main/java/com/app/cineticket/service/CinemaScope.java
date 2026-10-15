package com.app.cineticket.service;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.exception.BadRequestException;
import com.app.cineticket.exception.ForbiddenException;

/**
 * Regras de escopo da equipe de cinema: gerente e funcionário só agem no próprio cinema;
 * o admin age em qualquer um, escolhendo qual em cada requisição.
 */
public final class CinemaScope {

    /** Texto do parâmetro cinemaId na documentação das rotas do gerente. */
    public static final String CINEMA_ID_DOC = "cinemaId: obrigatório para o admin (cinema em que ele age); "
            + "o gerente pode omitir (vale o próprio cinema).";

    private CinemaScope() {
    }

    /** Cinema do gerente ou funcionário; sem cinema vinculado, 403. */
    public static Long cinemaIdOf(User user) {
        if (user.getCinema() == null) {
            throw new ForbiddenException("Usuário sem cinema vinculado.");
        }
        return user.getCinema().getId();
    }

    /**
     * Cinema em que o usuário age nas rotas do gerente. Gerente: o próprio (um {@code cinemaId}
     * diferente dá 403). Admin: o {@code cinemaId} informado, obrigatório (400 sem ele).
     */
    public static Long actingCinemaId(User user, Long cinemaId) {
        if (user.hasRole("ROLE_ADMIN")) {
            if (cinemaId == null) {
                throw new BadRequestException("Informe o cinemaId: o admin escolhe em qual cinema está agindo.");
            }
            return cinemaId;
        }
        Long own = cinemaIdOf(user);
        if (cinemaId != null && !own.equals(cinemaId)) {
            throw new ForbiddenException("Você só pode agir no seu cinema.");
        }
        return own;
    }

    /** 403 com a mensagem informada se o recurso for de outro cinema. */
    public static void requireSameCinema(User user, Long cinemaId, String message) {
        requireWithin(cinemaIdOf(user), cinemaId, message);
    }

    /** Como {@link #requireSameCinema}, mas com o escopo já resolvido; escopo null = rede toda (admin). */
    public static void requireWithin(Long cinemaScope, Long cinemaId, String message) {
        if (cinemaScope != null && !cinemaScope.equals(cinemaId)) {
            throw new ForbiddenException(message);
        }
    }
}
