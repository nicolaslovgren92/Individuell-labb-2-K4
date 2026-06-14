package com.example.smartreseassistent.service;

import com.example.smartreseassistent.dto.WeatherResponse;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class WeatherService {


    private final WebClient weatherWebClient;
    private final Retry retry;

    @Value("${api.weather.api-key:test-key}")
    private String apiKey;

    public WeatherService(
            @Qualifier("weatherWebClient") WebClient weatherWebClient,
            RetryRegistry retryRegistry) {
        this.weatherWebClient = weatherWebClient;
        this.retry = retryRegistry.retry("weatherRetry");
    }

    public Mono<WeatherResponse> getWeather(String city) {
        log.info("Hämtar väder för: {}", city);

        Mono<WeatherResponse> apiCall = Mono.defer(() ->
                weatherWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/data/2.5/weather")
                                .queryParam("q", city)
                                .queryParam("appid", apiKey)
                                .queryParam("units", "metric")
                                .build())
                        .retrieve()
                        .onStatus(
                                status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(
                                                new RuntimeException("Väder-API fel: " + response.statusCode())
                                        ))
                        )
                        .bodyToMono(WeatherResponse.class)
        );

        return apiCall
                .transformDeferred(RetryOperator.of(retry))
                .doOnError(e -> log.warn("Väder-API misslyckades: {}", e.getMessage()))
                .onErrorResume(e -> {
                    log.warn("Väder-fallback aktiverad för '{}'. Orsak: {}", city, e.getMessage());
                    return Mono.just(buildFallbackWeather());
                });
    }

    private WeatherResponse buildFallbackWeather() {
        WeatherResponse fallback = new WeatherResponse();
        WeatherResponse.Main main = new WeatherResponse.Main();
        main.setTemp(20.0);
        fallback.setMain(main);

        WeatherResponse.Weather weather = new WeatherResponse.Weather();
        weather.setDescription("Sunny");
        fallback.setWeatherList(java.util.List.of(weather));

        return fallback;
    }
}