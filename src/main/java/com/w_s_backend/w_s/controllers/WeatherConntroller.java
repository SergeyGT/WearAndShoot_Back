package com.w_s_backend.w_s.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.w_s_backend.w_s.DTOs.CurrentWeatherDto;
import com.w_s_backend.w_s.Services.WeatherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@Slf4j
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherConntroller {
    private final WeatherService _weatherService;

    @GetMapping("/current")
    public ResponseEntity<CurrentWeatherDto> getCurrent(
            @RequestParam String q) {   
        return ResponseEntity.ok(_weatherService.getCurrentWeather(q));
    }
    
}
