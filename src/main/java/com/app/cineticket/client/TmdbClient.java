package com.app.cineticket.client;

import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.client.TmdbSearchResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TmdbClient {

    private final RestClient restClient;
    private final String apiKey;

    public TmdbClient(
            @Value("${tmdb.api.url}") String baseUrl,
            @Value("${tmdb.api.key}") String apiKey) {

        this.apiKey = apiKey;
        
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public TmdbSearchResponseDTO searchMovieByName(String movieName) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("query", movieName)
                        .queryParam("api_key", apiKey)
                        .queryParam("language", "pt-BR")
                        .build())
                .retrieve()
                .body(TmdbSearchResponseDTO.class);
    }

    /**
     * Ficha completa de um filme pelo ID do TMDB numa chamada só: dados em português, créditos,
     * classificações por país e vídeos (trailers em português ou inglês).
     */
    public TmdbMovieDetailsDTO getMovieDetails(long tmdbId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{id}")
                        .queryParam("api_key", apiKey)
                        .queryParam("language", "pt-BR")
                        .queryParam("append_to_response", "credits,release_dates,videos")
                        .queryParam("include_video_language", "pt,en")
                        .build(tmdbId))
                .retrieve()
                .body(TmdbMovieDetailsDTO.class);
    }
}
