package com.example.smartreseassistent.service;

import com.example.smartreseassistent.dto.ActivityResponse;
import com.example.smartreseassistent.dto.RecommendationDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class ActivityService {

    private final WebClient activityWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ActivityService(
            @Qualifier("activityWebClient") WebClient activityWebClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.activityWebClient = activityWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("activityCircuitBreaker");
        this.retry = retryRegistry.retry("activityRetry");
    }

    /**
     * Hämtar aktiviteter baserat på stad och väder.
     *  = yttre lagret (övervakar totalt antal fel)
     * = inre lagret  (försöker om vid enstaka fel)
     */
    public Mono<ActivityResponse> getActivities(String city, String weatherCondition) {
        String category = mapWeatherToCategory(weatherCondition);
        log.info("Hämtar aktiviteter – stad: '{}', väder: '{}', kategori: '{}'",
                city, weatherCondition, category);

        return Mono.defer(() ->
                        activityWebClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/get")
                                        .queryParam("city", city)
                                        .queryParam("category", category)
                                        .build())
                                .retrieve()
                                .onStatus(
                                        status -> status.is4xxClientError() || status.is5xxServerError(),
                                        response -> Mono.error(
                                                new RuntimeException("Aktivitets-API fel: " + response.statusCode())
                                        )
                                )
                                .bodyToMono(ActivityResponse.class)
                                .map(response -> {
                                    response.setCity(city);
                                    response.setCategory(category);
                                    return response;
                                })
                )
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> log.warn("Aktivitets-API misslyckades: {}", e.getMessage()));
    }



    /**
     * Mappar ett väderförhållande till en aktivitetskategori.
     * Detta är logiken som gör rekommendationerna kontextuella.
     */
    public String mapWeatherToCategory(String condition) {
        if (condition == null) return "sightseeing";

        String lower = condition.toLowerCase();

        if (lower.contains("rain") || lower.contains("drizzle") || lower.contains("thunder")) {
            return "museum";
        } else if (lower.contains("snow") || lower.contains("sleet")) {
            return "cafe";
        } else if (lower.contains("cloud") || lower.contains("overcast")) {
            return "shopping";
        } else {
            // Sunny, Clear, etc.
            return "park";
        }
    }

    public Mono<ActivityResponse> getFallbackActivities(String city, String condition, Throwable t) {
        log.warn("CB-fallback aktiverad för '{}'. Orsak: {}", city, t.getMessage());
        return Mono.empty();
    }


    /**
     * Hårdkodad reservlista – används av RecommendationService
     * när activitiesFallback returnerar Mono.empty().
     */
    public List<RecommendationDto.Activity> getFallbackActivities() {
        return List.of(
                RecommendationDto.Activity.builder()
                        .name("Stadsmuseet")
                        .address("Stadshuset, Centrum")
                        .category("museum")
                        .build(),
                RecommendationDto.Activity.builder()
                        .name("Stadsparken")
                        .address("Parkvägen 1")
                        .category("park")
                        .build(),
                RecommendationDto.Activity.builder()
                        .name("Lokalt café")
                        .address("Stora Torget 3")
                        .category("cafe")
                        .build(),
                RecommendationDto.Activity.builder()
                        .name("Köpcentrum")
                        .address("Handelsvägen 5")
                        .category("shopping")
                        .build(),
                RecommendationDto.Activity.builder()
                        .name("Gamla Staden")
                        .address("Historiska kvarteret")
                        .category("sightseeing")
                        .build()
        );
    }
}
