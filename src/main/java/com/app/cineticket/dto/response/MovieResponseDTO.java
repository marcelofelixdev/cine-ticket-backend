package com.app.cineticket.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MovieResponseDTO (
        Long id,
        String titulo,
        String tituloOriginal,
        String sinopse,
        String tmdbId,
        String posterUrl,
        /** Imagem larga para a vitrine. */
        String backdropUrl,
        Integer duracaoEmMinutos,
        /** Classificação indicativa brasileira: L, 10, 12, 14, 16 ou 18 (null se desconhecida). */
        String classificacao,
        List<String> generos,
        String diretor,
        List<MovieCastDTO> elenco,
        String produtora,
        /** Link do trailer no YouTube (null se não houver). */
        String trailerUrl,
        LocalDate dataEstreia
) {}
