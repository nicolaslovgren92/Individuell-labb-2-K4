package com.example.smartreseassistent;

import com.example.smartreseassistent.service.WeatherService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class WeatherServiceTest {

    private MockWebServer mockWebServer;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Bygg WebClient som pekar på MockWebServer
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .clientConnector(new JdkClientHttpConnector())
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .jackson2JsonDecoder(new org.springframework.http.codec.json.Jackson2JsonDecoder()))
                .build();

        // Konfigurera Retry med kort väntetid för snabbare tester
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(RuntimeException.class)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retryRegistry.retry("weatherRetry", retryConfig);


        weatherService = new WeatherService(webClient, retryRegistry);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Förhindra dubbel-shutdown (t.ex. i shouldUseFallbackOnConnectionFailure)
        try {
            mockWebServer.shutdown();
        } catch (Exception ignored) {
            // Redan stängd
        }
    }

    // ─── Positivt test ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ska returnera väderdata vid lyckat API-anrop")
    void shouldReturnWeatherOnSuccess() {
        // Arrange – MockServer svarar med giltig JSON
        String json = """
                {
                  "main": {
                    "temp": 15.5
                  },
                  "weather": [
                    { "description": "clear sky" }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        // Act + Assert
        StepVerifier.create(weatherService.getWeather("Stockholm"))
                .assertNext(weather -> {
                    assertThat(weather.getConditionText()).isEqualTo("clear sky");
                    assertThat(weather.getTempC()).isEqualTo(15.5);
                })
                .verifyComplete();
    }

    // ─── Negativt test – Retry + Fallback ────────────────────────────────────

    @Test
    @DisplayName("Ska använda fallback när server returnerar 500 tre gånger")
    void shouldUseFallbackAfter3Failures() {
        // Arrange – kö 3 fel (en per retry-försök)
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // Act + Assert – fallback ska returnera "Sunny" och 20.0°C
        StepVerifier.create(weatherService.getWeather("Stockholm"))
                .assertNext(weather -> {
                    assertThat(weather.getConditionText()).isEqualTo("Sunny");
                    assertThat(weather.getTempC()).isEqualTo(20.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Ska använda fallback vid nätverksfel")
    void shouldUseFallbackOnConnectionFailure() throws IOException {
        // Stäng servern för att simulera connection refused
        mockWebServer.shutdown();

        StepVerifier.create(weatherService.getWeather("Stockholm"))
                .assertNext(weather -> {
                    assertThat(weather.getConditionText()).isEqualTo("Sunny");
                    assertThat(weather.getTempC()).isEqualTo(20.0);
                })
                .verifyComplete();
    }
}


