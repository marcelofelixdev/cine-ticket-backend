package com.app.cineticket.dto.response;

/** Pessoa do elenco; fotoUrl é null quando o TMDB não tem foto. */
public record MovieCastDTO(String nome, String personagem, String fotoUrl) {
}
