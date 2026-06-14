package com.example.smartreseassistent.controller;


import com.example.smartreseassistent.dto.RecommendationDto;
import com.example.smartreseassistent.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/recommendations")
    public Mono<ResponseEntity<RecommendationDto>> getRecommendations(
            @RequestParam String location) {

        if (location == null || location.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return recommendationService.getRecommendations(location)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}


