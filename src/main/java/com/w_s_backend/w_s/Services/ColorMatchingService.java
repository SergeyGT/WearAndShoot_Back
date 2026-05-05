package com.w_s_backend.w_s.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.w_s_backend.w_s.models.ColorScheme;

import java.util.*;

@Service
@Slf4j
public class ColorMatchingService {

    // Определяем базовые цвета и их группы
    private static final Map<String, ColorGroup> COLOR_GROUPS = new HashMap<>();
    
    static {
        // Красная группа
        COLOR_GROUPS.put("Красный", new ColorGroup("red", 0));
        COLOR_GROUPS.put("Бордовый", new ColorGroup("red", 10));
        COLOR_GROUPS.put("Розовый", new ColorGroup("red", 30));
        COLOR_GROUPS.put("Оранжевый", new ColorGroup("red", 40));
        
        // Жёлтая группа
        COLOR_GROUPS.put("Желтый", new ColorGroup("yellow", 60));
        
        // Зелёная группа
        COLOR_GROUPS.put("Зеленый", new ColorGroup("green", 120));
        COLOR_GROUPS.put("Хаки", new ColorGroup("green", 90));
        
        // Синяя группа
        COLOR_GROUPS.put("Синий", new ColorGroup("blue", 240));
        COLOR_GROUPS.put("Голубой", new ColorGroup("blue", 200));
        
        // Фиолетовая группа
        COLOR_GROUPS.put("Фиолетовый", new ColorGroup("purple", 280));
        
        // Нейтральные
        COLOR_GROUPS.put("Белый", new ColorGroup("neutral", -1));
        COLOR_GROUPS.put("Черный", new ColorGroup("neutral", -1));
        COLOR_GROUPS.put("Серый", new ColorGroup("neutral", -1));
        COLOR_GROUPS.put("Бежевый", new ColorGroup("neutral", -1));
        COLOR_GROUPS.put("Коричневый", new ColorGroup("neutral", -1));
    }
    
    /**
     * Проверяет, сочетаются ли цвета по заданной схеме
     */
    public boolean areColorsCompatible(String color1, String color2, ColorScheme scheme) {
        if (color1 == null || color2 == null) return true;
        if (scheme == ColorScheme.ANY) return true;
        
        ColorGroup group1 = COLOR_GROUPS.get(color1);
        ColorGroup group2 = COLOR_GROUPS.get(color2);
        
        if (group1 == null || group2 == null) return true;
        
        // Нейтральные цвета сочетаются со всем
        if (group1.group.equals("neutral") || group2.group.equals("neutral")) {
            return true;
        }
        
        return switch (scheme) {
            case MONOCHROME -> isMonochromatic(group1, group2);
            case COMPLEMENTARY -> isComplementary(group1, group2);
            case ANALOGOUS -> isAnalogous(group1, group2);
            case NEUTRAL -> group1.group.equals("neutral") && group2.group.equals("neutral");
            default -> true;
        };
    }
    
    /**
     * Проверяет, подходит ли вещь по цвету к уже выбранным вещам
     */
    public boolean matchesColorScheme(String newColor, List<String> existingColors, ColorScheme scheme) {
        if (existingColors.isEmpty()) return true;
        if (scheme == ColorScheme.ANY) return true;
        
        for (String existingColor : existingColors) {
            if (!areColorsCompatible(newColor, existingColor, scheme)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isMonochromatic(ColorGroup g1, ColorGroup g2) {
        // Один цвет или близкие оттенки (разница до 40°)
        return g1.group.equals(g2.group) && Math.abs(g1.hue - g2.hue) <= 40;
    }
    
    private boolean isComplementary(ColorGroup g1, ColorGroup g2) {
        // Противоположные цвета (разница 150°-210°)
        int diff = Math.abs(g1.hue - g2.hue);
        return diff >= 150 && diff <= 210;
    }
    
    private boolean isAnalogous(ColorGroup g1, ColorGroup g2) {
        // Соседние цвета (разница до 60°)
        if (!g1.group.equals(g2.group)) {
            int diff = Math.abs(g1.hue - g2.hue);
            return diff <= 60 || diff >= 300;
        }
        return true;
    }
    
    @lombok.AllArgsConstructor
    private static class ColorGroup {
        String group;
        int hue; // -1 для нейтральных
    }
}