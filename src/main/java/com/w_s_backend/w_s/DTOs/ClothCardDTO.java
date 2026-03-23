package com.w_s_backend.w_s.DTOs;

import com.w_s_backend.w_s.models.ClothStyle;
import com.w_s_backend.w_s.models.ClothingCategory;
import com.w_s_backend.w_s.models.Season;
import lombok.Data;

@Data
public class ClothCardDTO {
    private String clothName;
    private ClothingCategory category; 
    private ClothStyle style;
    private Season season;
    private Integer warmthLevel;
    private String color;
    private Long userId;
}
