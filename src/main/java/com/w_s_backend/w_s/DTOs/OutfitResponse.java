package com.w_s_backend.w_s.DTOs;

import java.time.LocalDateTime;
import java.util.List;

import com.w_s_backend.w_s.models.ClothingCategory;
import com.w_s_backend.w_s.models.OutfitStyle;

import lombok.Data;

@Data
public class OutfitResponse {
    private Long id;
    private String outfitName;
    private OutfitStyle style;
    private double temperatureC;
    private String weatherCondition;
    private Boolean isLiked;
    private LocalDateTime createdAt;
    private List<ClothCardShortDto> items;

    @Data
    public static class ClothCardShortDto {
        private Long id;
        private String clothName;
        private String imagePath;
        private ClothingCategory category;
    }
}