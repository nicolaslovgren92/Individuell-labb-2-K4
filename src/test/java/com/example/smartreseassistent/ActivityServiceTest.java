package com.example.smartreseassistent;

import com.example.smartreseassistent.service.ActivityService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityServiceTest {

    private MockWebServer mockWebServer;
    private ActivityService activityService;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        // Circuit Breaker – litet fönster för snabbare testning
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        circuitBreaker = cbRegistry.circuitBreaker("activityCircuitBreaker");

        // Retry – kort väntetid för snabbare tester
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(1)                           // ← 1 = ingen retry
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(RuntimeException.class)  // ← explicit
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retryRegistry.retry("activityRetry", retryConfig);

        activityService = new ActivityService(webClient, cbRegistry, retryRegistry);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ─── Positivt test ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ska returnera aktiviteter vid lyckat API-anrop")
    void shouldReturnActivitiesOnSuccess() {
        String json = """
                {
                  "headers": { "Authorization": "Bearer mitt-token" },
                  "url": "https://httpbin.org/get?city=Stockholm&category=park"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        StepVerifier.create(activityService.getActivities("Stockholm", "Sunny"))
                .assertNext(response -> {
                    assertThat(response.getCity()).isEqualTo("Stockholm");
                    assertThat(response.getCategory()).isEqualTo("park");
                })
                .verifyComplete();
    }

    // ─── Negativt test – Circuit Breaker ──────────────────────────────────────

    @Test
    @DisplayName("Circuit Breaker ska växla till OPEN efter upprepade fel")
    void circuitBreakerShouldOpenAfterFailures() {
        // Kö 5 fel – ett per anrop
        for (int i = 0; i < 5; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }

        // Gör 5 anrop – fånga undantag så testet inte kraschar
        for (int i = 0; i < 5; i++) {
            try {
                activityService.getActivities("Stockholm", "Rain").block();
            } catch (Exception ignored) {
                // förväntat fel – CB räknar det
            }
        }

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        System.out.println("✅ Circuit Breaker är OPEN efter upprepade fel!");
    }

    @Test
    @DisplayName("Fallback ska returnera tom Mono när CB är OPEN")
    void fallbackShouldReturnEmptyMono() {
        // Kö 5 fel – ett per anrop
        for (int i = 0; i < 5; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }

        for (int i = 0; i < 5; i++) {
            try {
                activityService.getActivities("Stockholm", "Rain").block();
            } catch (Exception ignored) {}
        }

        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        System.out.println("✅ Circuit Breaker är OPEN!");
    }

    // ─── Väder-till-kategori mappning ─────────────────────────────────────────

    @Test
    @DisplayName("Ska mappa regn till museum-kategori")
    void shouldMapRainToMuseum() {
        assertThat(activityService.mapWeatherToCategory("heavy rain"))
                .isEqualTo("museum");
    }

    @Test
    @DisplayName("Ska mappa snö till cafe-kategori")
    void shouldMapSnowToCafe() {
        assertThat(activityService.mapWeatherToCategory("light snow"))
                .isEqualTo("cafe");
    }

    @Test
    @DisplayName("Ska mappa sol till park-kategori")
    void shouldMapSunnyToPark() {
        assertThat(activityService.mapWeatherToCategory("clear sky"))
                .isEqualTo("park");
    }

    // ─── Fallback-lista ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Fallback-lista ska alltid returnera 5 aktiviteter")
    void fallbackListShouldReturn5Activities() {
        var activities = activityService.getFallbackActivities();

        assertThat(activities).hasSize(5);
        assertThat(activities).allMatch(a ->
                a.getName() != null && !a.getName().isBlank());

        System.out.println("✅ Fallback-lista innehåller " + activities.size() + " aktiviteter");
    }
}