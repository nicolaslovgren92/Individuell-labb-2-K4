package com.example.smartreseassistent.service;


import com.example.smartreseassistent.dto.ActivityResponse;
import com.example.smartreseassistent.dto.RecommendationDto;
import com.example.smartreseassistent.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final WeatherService weatherService;
    private final ActivityService activityService;

    /**
     * Huvudflödet:
     * 1. Hämta väder  (med Retry + fallback till "Sunny")
     * 2. Hämta aktiviteter baserat på vädret (med CircuitBreaker + Retry + fallback)
     * 3. Bygg och returnera RecommendationDto
     */
    public Mono<RecommendationDto> getRecommendations(String city) {
        log.info("Hämtar rekommendationer för: {}", city);

        return weatherService.getWeather(city)
                .flatMap(weather -> buildRecommendation(city, weather));
    }

    private Mono<RecommendationDto> buildRecommendation(String city, WeatherResponse weather) {
        String condition = weather.getConditionText();
        double temp = weather.getTempC();

        return activityService.getActivities(city, condition)

                // switchIfEmpty körs om getActivities returnerar Mono.empty()
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Inga aktiviteter – returnerar fallback-lista");
                    ActivityResponse empty = new ActivityResponse();
                    empty.setCity(city);
                    empty.setCategory("fallback");
                    return empty;
                }))

                .map(activityResponse -> {
                    boolean isFallback = "fallback".equals(activityResponse.getCategory());

                    List<RecommendationDto.Activity> activities = isFallback
                            ? activityService.getFallbackActivities()
                            : buildActivitiesFromResponse(activityResponse);

                    return RecommendationDto.builder()
                            .city(city)
                            .weather(condition)
                            .temperature(temp)
                            .activities(activities)
                            .fallback(isFallback)
                            .build();
                });
    }

    /**
     * Bygger aktivitetslista från httpbin-svaret.
     * httpbin returnerar inte riktiga aktiviteter, men vi visar
     * att anropet gick igenom och Bearer Token skickades korrekt.
     */
    private List<RecommendationDto.Activity> buildActivitiesFromResponse(
            ActivityResponse response) {

        String category = response.getCategory();
        String city = response.getCity();

        // Returnera kategori-anpassade aktiviteter baserat på vädret
        return switch (category) {
            case "museum" -> List.of(
                    new RecommendationDto.Activity("Stadsmuseet", city + " centrum", "museum"),
                    new RecommendationDto.Activity("Konstgalleriet", city + " gamla stan", "museum")
            );
            case "cafe" -> List.of(
                    new RecommendationDto.Activity("Kaféet på torget", city + " torg", "cafe"),
                    new RecommendationDto.Activity("Bokkaféet", city + " biblioteket", "cafe")
            );
            case "shopping" -> List.of(
                    new RecommendationDto.Activity("Köpcentrum", city + " centrum", "shopping"),
                    new RecommendationDto.Activity("Marknaden", city + " hamnen", "shopping")
            );
            default -> List.of(
                    new RecommendationDto.Activity("Stadsparken", city + " parken", "park"),
                    new RecommendationDto.Activity("Vandringsstig", city + " naturen", "park")
            );
        };
    }
}
