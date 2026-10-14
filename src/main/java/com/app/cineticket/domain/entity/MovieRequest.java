package com.app.cineticket.domain.entity;

import com.app.cineticket.domain.enums.MovieRequestStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Pedido de filme do gerente para o próprio cinema (tb_movie_request). Título, ano e pôster
 * são uma cópia do TMDB no momento do pedido, para o admin ver o que está aprovando.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "tb_movie_request")
public class MovieRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(nullable = false, length = 150)
    private String titulo;

    private Integer ano;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovieRequestStatus status;

    @Column(name = "motivo_recusa", length = 500)
    private String motivoRecusa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Filme do catálogo gerado (ou reaproveitado) na aprovação. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;
}
