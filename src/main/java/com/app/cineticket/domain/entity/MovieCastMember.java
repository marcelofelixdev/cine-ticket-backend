package com.app.cineticket.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ator ou atriz do elenco principal de um filme (tb_movie_elenco). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class MovieCastMember {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 150)
    private String personagem;

    /** Foto do TMDB (w185), ou null. */
    @Column(name = "foto_url", length = 255)
    private String fotoUrl;

    public MovieCastMember(String nome, String personagem) {
        this(nome, personagem, null);
    }
}
