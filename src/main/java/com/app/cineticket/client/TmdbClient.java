package com.app.cineticket.client;

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
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
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
}
