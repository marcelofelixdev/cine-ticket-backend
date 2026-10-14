package com.app.cineticket.demo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados fixos do perfil demo. Os filmes guardam o ID do TMDB e um plano B (título, duração,
 * direção e gêneros): a ficha completa (sinopse, pôster, elenco, classificação, trailer)
 * vem do TMDB no boot.
 */
public final class DemoCatalog {

    private DemoCatalog() {
    }

    public record DemoMovie(long tmdbId, String titulo, int duracaoMinutos, String diretor, String generos) {
    }

    public record DemoRoom(String nome, int capacidade, BigDecimal precoBase) {
    }

    public record DemoCinema(String nome, String cnpj, String endereco, String cidade, String uf, List<DemoRoom> salas) {
    }

    /** Quantos dias à frente o demo mantém sessões programadas. */
    public static final int DAYS_AHEAD = 7;

    /** Acréscimo no preço aos sábados e domingos. */
    public static final BigDecimal WEEKEND_SURCHARGE = new BigDecimal("6.00");

    /** Filmes brasileiros: sessões com áudio NACIONAL. */
    public static final java.util.Set<Long> NATIONAL_MOVIES = java.util.Set.of(598L, 1000837L);

    private static final String NOLAN = "Christopher Nolan";
    private static final String DRAMA_CRIME = "Drama, Crime";

    public static final List<DemoMovie> MOVIES = List.of(
            // Clássicos
            new DemoMovie(238, "O Poderoso Chefão", 175, "Francis Ford Coppola", DRAMA_CRIME),
            new DemoMovie(105, "De Volta para o Futuro", 116, "Robert Zemeckis", "Aventura, Comédia, Ficção científica"),
            new DemoMovie(13, "Forrest Gump: O Contador de Histórias", 142, "Robert Zemeckis", "Comédia, Drama, Romance"),
            new DemoMovie(680, "Pulp Fiction: Tempo de Violência", 154, "Quentin Tarantino", "Thriller, Crime"),
            new DemoMovie(278, "Um Sonho de Liberdade", 142, "Frank Darabont", DRAMA_CRIME),
            new DemoMovie(550, "Clube da Luta", 139, "David Fincher", "Drama"),
            new DemoMovie(603, "Matrix", 136, "Lana Wachowski, Lilly Wachowski", "Ação, Ficção científica"),
            new DemoMovie(129, "A Viagem de Chihiro", 125, "Hayao Miyazaki", "Animação, Família, Fantasia"),
            new DemoMovie(598, "Cidade de Deus", 130, "Fernando Meirelles", DRAMA_CRIME),
            new DemoMovie(155, "Batman: O Cavaleiro das Trevas", 152, NOLAN, "Ação, Crime, Drama"),
            new DemoMovie(27205, "A Origem", 148, NOLAN, "Ação, Ficção científica, Aventura"),
            // Recentes
            new DemoMovie(157336, "Interestelar", 169, NOLAN, "Aventura, Drama, Ficção científica"),
            new DemoMovie(496243, "Parasita", 133, "Bong Joon-ho", "Comédia, Thriller, Drama"),
            new DemoMovie(872585, "Oppenheimer", 180, NOLAN, "Drama, História"),
            new DemoMovie(693134, "Duna: Parte Dois", 167, "Denis Villeneuve", "Ficção científica, Aventura"),
            new DemoMovie(1022789, "Divertida Mente 2", 97, "Kelsey Mann", "Animação, Família, Comédia"),
            new DemoMovie(1000837, "Ainda Estou Aqui", 137, "Walter Salles", "Drama, História")
    );

    /** CNPJs fictícios, só para demonstração. */
    public static final List<DemoCinema> CINEMAS = List.of(
            new DemoCinema("CineTicket Paulista", "00.000.000/0001-91",
                    "Av. Paulista, 1000 - Bela Vista, São Paulo/SP", "São Paulo", "SP",
                    List.of(new DemoRoom("Sala 1 - IMAX", 120, new BigDecimal("42.00")),
                            new DemoRoom("Sala 2", 80, new BigDecimal("30.00")))),
            new DemoCinema("CineTicket Copacabana", "00.000.000/0002-72",
                    "Av. Atlântica, 2000 - Copacabana, Rio de Janeiro/RJ", "Rio de Janeiro", "RJ",
                    List.of(new DemoRoom("Sala 1", 100, new BigDecimal("30.00")),
                            new DemoRoom("Sala 2 - VIP", 40, new BigDecimal("50.00"))))
    );
}
