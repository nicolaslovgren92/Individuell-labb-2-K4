package com.example.smartreseassistent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${api.weather.base-url}")
    private String weatherBaseUrl;

    @Value("${api.activity.base-url}")
    private String activityBaseUrl;

    @Value("${api.activity.token}")
    private String activityToken;

    /**
     * Väder-API WebClient.
     * Autentisering: API-nyckel skickas som Query Parameter (osäker metod).
     * Nyckeln läggs till per anrop i WeatherService, inte här.
     */
    @Bean("weatherWebClient")
    public WebClient weatherWebClient() {
        return WebClient.builder()
                .baseUrl(weatherBaseUrl)
                .build();
    }

    /**
     * Aktivitets-API WebClient.
     * Autentisering: Bearer Token i Authorization-header (säker metod).
     * Tokenen sätts här en gång och gäller alla anrop automatiskt.
     */
    @Bean("activityWebClient")
    public WebClient activityWebClient() {
        return WebClient.builder()
                .baseUrl(activityBaseUrl)
                .defaultHeader("Authorization", "Bearer " + activityToken)
                .build();
    }
}
