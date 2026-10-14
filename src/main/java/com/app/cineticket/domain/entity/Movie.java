package com.app.cineticket.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "tb_movie")
public class Movie extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String sinopse;

    @Column(name = "tmdb_id", unique = true)
    private String tmdbId;

    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoEmMinutos;

    @Column(name = "titulo_original", length = 255)
    private String tituloOriginal;

    /** Imagem larga (paisagem) usada como capa na vitrine. */
    @Column(name = "backdrop_url", length = 500)
    private String backdropUrl;

    /** Um ou mais diretores, separados por vírgula. */
    @Column(length = 255)
    private String diretor;

    /** Até três produtoras, separadas por vírgula. */
    @Column(length = 255)
    private String produtora;

    /** Gêneros em português, separados por vírgula. */
    @Column(length = 255)
    private String generos;

    /** Classificação indicativa brasileira: L, 10, 12, 14, 16 ou 18. */
    @Column(length = 2)
    private String classificacao;

    @Column(name = "trailer_youtube_key", length = 32)
    private String trailerYoutubeKey;

    @Column(name = "data_estreia")
    private java.time.LocalDate dataEstreia;

    @Column(name = "tmdb_sincronizado_em")
    private java.time.LocalDateTime tmdbSincronizadoEm;

    /** Elenco principal, na ordem de destaque. */
    @ElementCollection
    @CollectionTable(name = "tb_movie_elenco", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "ordem")
    @org.hibernate.annotations.BatchSize(size = 50)
    private java.util.List<MovieCastMember> elenco = new java.util.ArrayList<>();

}
