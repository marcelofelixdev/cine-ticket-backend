package com.app.cineticket.dto.request;

import com.app.cineticket.domain.enums.SessionAudio;

/**
 * Filtros opcionais da vitrine (filmes em cartaz).
 *
 * @param cinemaId      só filmes em cartaz neste cinema
 * @param cidade        só filmes em cartaz nesta cidade
 * @param formato       só filmes com sessão futura 2D ou 3D
 * @param audio         só filmes com sessão futura dublada, legendada ou nacional
 * @param classificacao só filmes desta classificação indicativa (L, 10, 12, 14, 16 ou 18)
 */
public record MovieFilter(Long cinemaId, String cidade, String formato, SessionAudio audio, String classificacao) {

    public boolean bySession() {
        return formato != null || audio != null;
    }
}
