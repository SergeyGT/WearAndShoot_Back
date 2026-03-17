package com.w_s_backend.w_s.DTOs;

import lombok.Data;

@Data
public class CurrentWeatherDto {
    private Location location;
    private Current current;

    @Data
    public static class Location {
        private String name;
        private String region;
        private String country;
        private double lat;
        private double lon;
        private String localtime;
    }

    @Data
    public static class Current {
        private double temp_c;
        private double temp_f;
        private Condition condition;
        private double wind_kph;
        private double wind_mph;
        private int humidity;
        private double feelslike_c;
        private double uv;
    }

    @Data
    public static class Condition {
        private String text;
        private String icon;    
        private int code;
    }
    
}
