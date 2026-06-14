package com.example.smartreseassistent.dto;


import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecommendationDto {

    private String city;
    private String weather;
    private double temperature;
    private List<Activity> activities;
    private boolean fallback;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Activity {
        private String name;
        private String address;
        private String category;
    }


}
