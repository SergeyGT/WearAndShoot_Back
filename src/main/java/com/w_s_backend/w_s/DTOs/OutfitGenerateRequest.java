package com.w_s_backend.w_s.DTOs;

import com.w_s_backend.w_s.models.OutfitStyle;

import lombok.Data;

@Data
public class OutfitGenerateRequest {
    private OutfitStyle style;
    private int count = 3;
}
