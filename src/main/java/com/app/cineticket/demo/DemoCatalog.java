package com.app.cineticket.demo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados fixos do perfil demo. Os filmes guardam só o ID do TMDB e um plano B
 * (título e duração): sinopse, pôster e título em português vêm do TMDB no boot.
 */
public final class DemoCatalog {

    private DemoCatalog() {
    }

    public record DemoMovie(long tmdbId, String titulo, int duracaoMinutos) {
    }

    public record DemoRoom(String nome, int capacidade, BigDecimal precoBase) {
    }

    public record DemoCinema(String nome, String cnpj, String endereco, List<DemoRoom> salas) {
    }

    /** Quantos dias à frente o demo mantém sessões programadas. */
    public static final int DAYS_AHEAD = 7;

    /** Acréscimo no preço aos sábados e domingos. */
    public static final BigDecimal WEEKEND_SURCHARGE = new BigDecimal("6.00");

    public static final List<DemoMovie> MOVIES = List.of(
            // Clássicos
            new DemoMovie(238, "O Poderoso Chefão", 175),
            new DemoMovie(105, "De Volta para o Futuro", 116),
            new DemoMovie(13, "Forrest Gump: O Contador de Histórias", 142),
            new DemoMovie(680, "Pulp Fiction: Tempo de Violência", 154),
            new DemoMovie(278, "Um Sonho de Liberdade", 142),
            new DemoMovie(550, "Clube da Luta", 139),
            new DemoMovie(603, "Matrix", 136),
            new DemoMovie(129, "A Viagem de Chihiro", 125),
            new DemoMovie(598, "Cidade de Deus", 130),
            new DemoMovie(155, "Batman: O Cavaleiro das Trevas", 152),
            new DemoMovie(27205, "A Origem", 148),
            // Recentes
            new DemoMovie(157336, "Interestelar", 169),
            new DemoMovie(496243, "Parasita", 133),
            new DemoMovie(872585, "Oppenheimer", 180),
            new DemoMovie(693134, "Duna: Parte Dois", 167),
            new DemoMovie(1022789, "Divertida Mente 2", 97),
            new DemoMovie(1000837, "Ainda Estou Aqui", 137)
    );

    /** CNPJs fictícios, só para demonstração. */
    public static final List<DemoCinema> CINEMAS = List.of(
            new DemoCinema("CineTicket Paulista", "00.000.000/0001-91",
                    "Av. Paulista, 1000 - Bela Vista, São Paulo/SP",
                    List.of(new DemoRoom("Sala 1 - IMAX", 120, new BigDecimal("42.00")),
                            new DemoRoom("Sala 2", 80, new BigDecimal("30.00")))),
            new DemoCinema("CineTicket Copacabana", "00.000.000/0002-72",
                    "Av. Atlântica, 2000 - Copacabana, Rio de Janeiro/RJ",
                    List.of(new DemoRoom("Sala 1", 100, new BigDecimal("30.00")),
                            new DemoRoom("Sala 2 - VIP", 40, new BigDecimal("50.00"))))
    );
}
