package com.example.smartreseassistent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

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
    @Bean
    public JdkClientHttpConnector jdkClientHttpConnector() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new JdkClientHttpConnector(httpClient);
    }

    /**
     * Aktivitets-API WebClient.
     * Autentisering: Bearer Token i Authorization-header (säker metod).
     * Tokenen sätts här en gång och gäller alla anrop automatiskt.
     */
    @Bean("weatherWebClient")
    public WebClient weatherWebClient(JdkClientHttpConnector connector) {
        return WebClient.builder()
                .baseUrl(weatherBaseUrl)
                .clientConnector(connector)
                .build();
    }
    @Bean("activityWebClient")
    public WebClient activityWebClient(JdkClientHttpConnector connector) {
        return WebClient.builder()
                .baseUrl(activityBaseUrl)
                .clientConnector(connector)
                .defaultHeader("Authorization", "Bearer " + activityToken)
                .build();
    }
}
