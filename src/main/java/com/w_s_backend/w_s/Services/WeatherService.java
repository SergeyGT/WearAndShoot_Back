package com.w_s_backend.w_s.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.w_s_backend.w_s.DTOs.CurrentWeatherDto;

@Service
public class WeatherService {
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public WeatherService(
            RestTemplate restTemplate,
            @Value("${weatherapi.key}") String apiKey,
            @Value("${weatherapi.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public CurrentWeatherDto getCurrentWeather(String query){
        String url = baseUrl + "/current.json?key=" + apiKey + "&q=" + query + "&lang=ru";

        return restTemplate.getForObject(url, CurrentWeatherDto.class);
    }
}
