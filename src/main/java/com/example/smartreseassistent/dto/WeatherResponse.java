package com.example.smartreseassistent.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class WeatherResponse {

        @JsonProperty("main")
        private Main main;

        @JsonProperty("weather")
        private List<Weather> weatherList;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Main {
            @JsonProperty("temp")
            private double temp;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Weather {
            @JsonProperty("description")
            private String description;  // t.ex. "clear sky", "light rain"
        }

        public String getConditionText() {
            if (weatherList != null && !weatherList.isEmpty()) {
                return weatherList.getFirst().getDescription();
            }
            return "Sunny";
        }

        public double getTempC() {
            return main != null ? main.getTemp() : 20.0;
        }
    }
