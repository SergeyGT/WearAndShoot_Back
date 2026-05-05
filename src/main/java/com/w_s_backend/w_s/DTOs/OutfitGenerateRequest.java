package com.w_s_backend.w_s.DTOs;

import com.w_s_backend.w_s.models.ColorScheme;
import com.w_s_backend.w_s.models.OutfitStyle;

import lombok.Data;

@Data
public class OutfitGenerateRequest {
    private Long userId;   
    private OutfitStyle style;
    private int count = 3;
    private String outfitName;
    private ColorScheme colorScheme = ColorScheme.ANY;
}
