package com.example.smartreseassistent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Properties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityResponse {


        // httpbin returnerar tillbaka headers som bekräftelse
        @JsonProperty("headers")
        private Map<String, String> headers;

        // httpbin returnerar URL:en som anropades
        @JsonProperty("url")
        private String url;

        // Vi lägger till kategori manuellt efter att vi fått svar
        private String category;
        private String city;
    }






