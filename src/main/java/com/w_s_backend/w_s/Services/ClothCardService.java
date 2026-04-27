package com.w_s_backend.w_s.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.DTOs.CurrentWeatherDto;
import com.w_s_backend.w_s.Repositories.ClothCardPepository;
import com.w_s_backend.w_s.Repositories.OutfitRepository;
import com.w_s_backend.w_s.models.ClothCard;
import com.w_s_backend.w_s.models.ClothStyle;
import com.w_s_backend.w_s.models.ClothingCategory;
import com.w_s_backend.w_s.models.Outfit;
import com.w_s_backend.w_s.models.OutfitStyle;
import com.w_s_backend.w_s.models.User;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ClothCardService {
    private final ClothCardPepository _clothCardPepository;
    private final UserService _userService;
    private final OutfitRepository outfitRepository;
    private final WeatherService weatherService;

    private final String UPLOAD_DIR = "uploads/images/";

    public ClothCard createCard(ClothCardDTO clothCardDTO, MultipartFile image){
        if(clothCardDTO.getClothName().isEmpty()) {
            //throw new ApiRequestException("Empty or Null Request data!");
        }

        User user = _userService.findById(clothCardDTO.getUserId());
        String imagePath = SaveImage(image, user.getId());

        ClothCard createdCard = ClothCard.builder()
            .clothName(clothCardDTO.getClothName())
            .category(clothCardDTO.getCategory())
            .imagePath(imagePath)
            .style(clothCardDTO.getStyle())
            .color(clothCardDTO.getColor())
            .season(clothCardDTO.getSeason())
            .warmthLevel(clothCardDTO.getWarmthLevel())
            .user(user)
            .build();

        ClothCard clothCard = _clothCardPepository.save(createdCard);

        return clothCard;
    }

    public ClothCard getCardById(Long cardId) {
        return _clothCardPepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found"));
    }

    public ClothCard updateCard(Long cartID, ClothCardDTO clothCardDTO, MultipartFile image){
        ClothCard clothCard = _clothCardPepository.findById(cartID)
                                .orElseThrow(() -> new RuntimeException("Card not found"));

        if(!clothCard.getUser().getId().equals(clothCardDTO.getUserId())){
            throw new RuntimeException("You can only update your own cards");
        }

        clothCard.setCategory(clothCardDTO.getCategory());
        clothCard.setClothName(clothCardDTO.getClothName());
        clothCard.setColor(clothCardDTO.getColor());
        clothCard.setStyle(clothCardDTO.getStyle());
        clothCard.setSeason(clothCardDTO.getSeason());
        clothCard.setWarmthLevel(clothCardDTO.getWarmthLevel());

        if(image != null && !image.isEmpty()){
            String oldPath = clothCard.getImagePath();
            String newPath = SaveImage(image, clothCard.getUser().getId());
            clothCard.setImagePath(newPath);

            if(oldPath != null && !oldPath.isEmpty()){
                deleteImagePath(oldPath);
            }
        }

        return _clothCardPepository.save(clothCard);
    }

    public List<ClothCard> readAllCards(Long id){
        return _clothCardPepository.findByUserId(id);
    }

    public void deleteImagePath(String path){
        try{
            Files.deleteIfExists(Paths.get(path));
        } catch(IOException ex){
            log.warn("Failed to delete old image: {}", path, ex);
        }
    }

    private String SaveImage(MultipartFile image, Long userId){
        if (image == null || image.isEmpty()) {
            return "";
        }
        
        try {
            String userUploadDir = UPLOAD_DIR + "user_" + userId + "/";
            Path uploadPath = Paths.get(userUploadDir);
            
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            String originalFileName = image.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;
            
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(image.getInputStream(), filePath);
            
            return userUploadDir + fileName;
            
        } catch (IOException e) {
            log.error("Error saving image", e);
            return "";
        }
    }

    @Transactional
    public List<Outfit> generateAndSaveOutfits(Long userId, OutfitStyle style, int count) {
        User user = _userService.findById(userId);
    List<ClothCard> allCards = _clothCardPepository.findByUserId(userId);

    if (allCards.isEmpty()) {
        throw new IllegalStateException("У пользователя нет вещей для генерации образов");
    }

    // Группируем вещи по категориям
    Map<ClothingCategory, List<ClothCard>> cardsByCategory = allCards.stream()
        .collect(Collectors.groupingBy(ClothCard::getCategory));

    // ОБЯЗАТЕЛЬНЫЕ категории для любого образа
    List<ClothingCategory> mandatoryCategories = Arrays.asList(
        ClothingCategory.TOP_BASE,  // Верх (футболка, рубашка и т.д.)
        ClothingCategory.BOTTOM      // Низ (брюки, юбка, шорты и т.д.)
    );

    // Проверяем наличие обязательных категорий
    List<String> missingMandatory = new ArrayList<>();
    for (ClothingCategory cat : mandatoryCategories) {
        List<ClothCard> cards = cardsByCategory.get(cat);
        if (cards == null || cards.isEmpty()) {
            missingMandatory.add(getCategoryDisplayName(cat));
        }
    }

    if (!missingMandatory.isEmpty()) {
        throw new IllegalStateException(
            "Для создания образа необходимы: " + String.join(" и ", missingMandatory) +
            ". Добавьте недостающие вещи в гардероб."
        );
    }

    // Определяем количество образов на основе разнообразия гардероба
    int maxOutfits = calculateMaxOutfits(allCards, cardsByCategory);
    int outfitsToGenerate = Math.min(Math.max(1, count), maxOutfits);
    
    log.info("Генерация {} образов из {} возможных (всего вещей: {})", 
             outfitsToGenerate, maxOutfits, allCards.size());

    List<Outfit> outfits = new ArrayList<>();

    // Получаем погоду
    CurrentWeatherDto weather = null;
    try {
        weather = weatherService.getCurrentWeather("Moscow");
    } catch (Exception e) {
        log.warn("Не удалось получить погоду, используем значения по умолчанию", e);
    }

    double temp = weather != null ? weather.getCurrent().getTemp_c() : 15.0;
    String condition = weather != null ? weather.getCurrent().getCondition().getText() : "Ясно";

    // Генерируем образы
    Set<Set<Long>> usedCombinations = new HashSet<>();
    
    for (int i = 1; i <= outfitsToGenerate; i++) {
        try {
            Outfit outfit = generateSingleOutfit(
                user, allCards, cardsByCategory, style, temp, condition, i, usedCombinations
            );
            
            if (outfit.getItems().size() >= 2) {  // Минимум верх и низ
                outfitRepository.save(outfit);
                outfits.add(outfit);
                usedCombinations.add(
                    outfit.getItems().stream()
                        .map(ClothCard::getId)
                        .collect(Collectors.toSet())
                );
                log.info("Сгенерирован образ #{}: {} предметов", i, outfit.getItems().size());
            } else {
                log.warn("Образ #{} не соответствует минимальным требованиям", i);
            }
        } catch (Exception e) {
            log.warn("Не удалось сгенерировать образ #{}: {}", i, e.getMessage());
        }
    }

    if (outfits.isEmpty()) {
        throw new IllegalStateException(
            "Не удалось сгенерировать ни одного образа. Попробуйте добавить больше вещей в гардероб."
        );
    }

    return outfits;
    }

    private int calculateMaxOutfits(List<ClothCard> allCards, Map<ClothingCategory, List<ClothCard>> cardsByCategory) {
    int topBaseCount = cardsByCategory.getOrDefault(ClothingCategory.TOP_BASE, Collections.emptyList()).size();
    int bottomCount = cardsByCategory.getOrDefault(ClothingCategory.BOTTOM, Collections.emptyList()).size();
    
    // Максимальное количество уникальных комбинаций верх + низ
    int maxCombinations = topBaseCount * bottomCount;
    
    int totalCards = allCards.size();
    
    if (totalCards <= 5) return Math.min(1, maxCombinations);
    if (totalCards <= 10) return Math.min(2, maxCombinations);
    if (totalCards <= 20) return Math.min(3, maxCombinations);
    return Math.min(4, maxCombinations);
}

    private Outfit generateSingleOutfit(
        User user,
        List<ClothCard> allCards,
        Map<ClothingCategory, List<ClothCard>> cardsByCategory,
        OutfitStyle style,
        double temp,
        String condition,
        int index,
        Set<Set<Long>> usedCombinations
) {
    List<ClothCard> selected = new ArrayList<>();
    Set<Long> usedCardIds = new HashSet<>();

    // 1. ОБЯЗАТЕЛЬНО выбираем TOP_BASE (верх)
    List<ClothCard> topBaseCandidates = cardsByCategory.getOrDefault(
        ClothingCategory.TOP_BASE, Collections.emptyList()
    ).stream()
        .filter(c -> matchesStyle(c, style))
        .filter(c -> matchesWeather(c, temp))
        .collect(Collectors.toList());

    if (topBaseCandidates.isEmpty()) {
        // Если нет подходящих по стилю, берем любые
        topBaseCandidates = cardsByCategory.getOrDefault(
            ClothingCategory.TOP_BASE, Collections.emptyList()
        );
    }

    if (!topBaseCandidates.isEmpty()) {
        ClothCard top = topBaseCandidates.get(new Random().nextInt(topBaseCandidates.size()));
        selected.add(top);
        usedCardIds.add(top.getId());
    }

    // 2. ОБЯЗАТЕЛЬНО выбираем BOTTOM (низ)
    List<ClothCard> bottomCandidates = cardsByCategory.getOrDefault(
        ClothingCategory.BOTTOM, Collections.emptyList()
    ).stream()
        .filter(c -> !usedCardIds.contains(c.getId()))
        .filter(c -> matchesStyle(c, style))
        .filter(c -> matchesWeather(c, temp))
        .collect(Collectors.toList());

    if (bottomCandidates.isEmpty()) {
        bottomCandidates = cardsByCategory.getOrDefault(
            ClothingCategory.BOTTOM, Collections.emptyList()
        ).stream()
            .filter(c -> !usedCardIds.contains(c.getId()))
            .collect(Collectors.toList());
    }

    if (!bottomCandidates.isEmpty()) {
        ClothCard bottom = bottomCandidates.get(new Random().nextInt(bottomCandidates.size()));
        selected.add(bottom);
        usedCardIds.add(bottom.getId());
    }

    // 3. ОПЦИОНАЛЬНО добавляем SHOES (обувь)
    List<ClothCard> shoesCandidates = cardsByCategory.getOrDefault(
        ClothingCategory.SHOES, Collections.emptyList()
    ).stream()
        .filter(c -> !usedCardIds.contains(c.getId()))
        .filter(c -> matchesStyle(c, style))
        .filter(c -> matchesWeather(c, temp))
        .collect(Collectors.toList());

    if (!shoesCandidates.isEmpty()) {
        ClothCard shoes = shoesCandidates.get(new Random().nextInt(shoesCandidates.size()));
        selected.add(shoes);
        usedCardIds.add(shoes.getId());
    }

    // 4. ОПЦИОНАЛЬНО добавляем верхний слой в зависимости от погоды
    if (temp <= 15) {
        List<ClothCard> outerCandidates = new ArrayList<>();
        
        // Сначала пробуем TOP_MID (средний слой)
        List<ClothCard> midCandidates = cardsByCategory.getOrDefault(
            ClothingCategory.TOP_MID, Collections.emptyList()
        ).stream()
            .filter(c -> !usedCardIds.contains(c.getId()))
            .filter(c -> matchesStyle(c, style))
            .filter(c -> matchesWeather(c, temp))
            .collect(Collectors.toList());
        
        outerCandidates.addAll(midCandidates);
        
        // Если холодно, добавляем TOP_OUTER (верхняя одежда)
        if (temp <= 10) {
            List<ClothCard> outerLayerCandidates = cardsByCategory.getOrDefault(
                ClothingCategory.TOP_OUTER, Collections.emptyList()
            ).stream()
                .filter(c -> !usedCardIds.contains(c.getId()))
                .filter(c -> matchesStyle(c, style))
                .filter(c -> matchesWeather(c, temp))
                .collect(Collectors.toList());
            
            outerCandidates.addAll(outerLayerCandidates);
        }
        
        if (!outerCandidates.isEmpty()) {
            ClothCard outer = outerCandidates.get(new Random().nextInt(outerCandidates.size()));
            selected.add(outer);
            usedCardIds.add(outer.getId());
        }
    }

    // 5. ОПЦИОНАЛЬНО добавляем HEAD (головной убор) если холодно
    if (temp <= 5) {
        List<ClothCard> headCandidates = cardsByCategory.getOrDefault(
            ClothingCategory.HEAD, Collections.emptyList()
        ).stream()
            .filter(c -> !usedCardIds.contains(c.getId()))
            .filter(c -> matchesStyle(c, style))
            .collect(Collectors.toList());
        
        if (!headCandidates.isEmpty()) {
            ClothCard head = headCandidates.get(new Random().nextInt(headCandidates.size()));
            selected.add(head);
            usedCardIds.add(head.getId());
        }
    }

    // 6. ОПЦИОНАЛЬНО добавляем ACCESSORY (аксессуары)
    List<ClothCard> accessoryCandidates = cardsByCategory.getOrDefault(
        ClothingCategory.ACCESSORY, Collections.emptyList()
    ).stream()
        .filter(c -> !usedCardIds.contains(c.getId()))
        .filter(c -> matchesStyle(c, style))
        .collect(Collectors.toList());
    
    if (!accessoryCandidates.isEmpty() && new Random().nextBoolean()) {  // 50% шанс
        ClothCard accessory = accessoryCandidates.get(new Random().nextInt(accessoryCandidates.size()));
        selected.add(accessory);
        usedCardIds.add(accessory.getId());
    }

    return Outfit.builder()
        .user(user)
        .style(style)
        .outfitName(style.name() + " образ #" + index)
        .temperatureC(temp)
        .weatherCondition(condition)
        .items(selected)
        .isLiked(false)
        .createdAt(java.time.LocalDateTime.now())
        .build();
}

    private List<ClothingCategory> getRequiredCategoriesList(OutfitStyle style, double temp) {
        List<ClothingCategory> required = new ArrayList<>();

        // Базовые категории для любого образа
        required.add(ClothingCategory.TOP_BASE);
        required.add(ClothingCategory.BOTTOM);
        required.add(ClothingCategory.SHOES);

        // Дополнительные категории в зависимости от стиля и температуры
        if (temp <= 15 || style == OutfitStyle.BUSINESS_CASUAL || style == OutfitStyle.OFFICE_FORMAL) {
            required.add(ClothingCategory.TOP_MID);
        }

        if (temp <= 10 || style == OutfitStyle.WINTER_CASUAL) {
            required.add(ClothingCategory.TOP_OUTER);
        }

        if (temp <= 5 || style == OutfitStyle.WINTER_CASUAL || style == OutfitStyle.STREETWEAR) {
            required.add(ClothingCategory.HEAD);
        }

        if (style == OutfitStyle.ELEGANT || style == OutfitStyle.BUSINESS_CASUAL) {
            required.add(ClothingCategory.ACCESSORY);
        }

        // Убираем теплые слои при жаре
        if (temp > 25) {
            required.remove(ClothingCategory.TOP_MID);
            required.remove(ClothingCategory.TOP_OUTER);
            required.remove(ClothingCategory.HEAD);
        }

        return required;
    }

    private boolean matchesStyle(ClothCard card, OutfitStyle outfitStyle) {
        if (card.getStyle() == null) return true;

        return switch (outfitStyle) {
            case BUSINESS_CASUAL, OFFICE_FORMAL, ELEGANT -> 
                card.getStyle() == ClothStyle.BUSINESS || card.getStyle() == ClothStyle.CASUAL;
            
            case SMART_CASUAL, CASUAL, STREETWEAR -> 
                card.getStyle() == ClothStyle.CASUAL || card.getStyle() == ClothStyle.SPORT;
            
            case SPORTY -> 
                card.getStyle() == ClothStyle.SPORT;
            
            case WINTER_CASUAL, SUMMER_VACATION -> 
                true;
            
            default -> true;
        };
    }

    // Новые методы для системы лайков
    @Transactional
    public Outfit toggleLikeOutfit(Long outfitId, Long userId) {
        Outfit outfit = outfitRepository.findById(outfitId)
            .orElseThrow(() -> new RuntimeException("Образ не найден"));
        
        // Проверяем, что образ принадлежит пользователю
        if (!outfit.getUser().getId().equals(userId)) {
            throw new RuntimeException("Вы можете лайкать только свои образы");
        }
        
        outfit.setIsLiked(!outfit.getIsLiked());
        return outfitRepository.save(outfit);
    }

    public List<Outfit> getLikedOutfits(Long userId) {
        // Нужно добавить метод в репозиторий
        return outfitRepository.findByUserIdAndIsLikedTrue(userId);
    }

    public List<Outfit> getUserOutfits(Long userId) {
        return outfitRepository.findByUserId(userId);
    }

    private boolean matchesWeather(ClothCard card, double temp) {
        if (card.getWarmthLevel() == null) return true;

        int warmth = card.getWarmthLevel();

        // Логика подбора по теплоте
        boolean tempMatch = switch (warmth) {
            case 1 -> temp >= 20;                    
            case 2 -> temp >= 15 && temp <= 30;     
            case 3 -> temp >= 5 && temp <= 25;       
            case 4 -> temp >= -5 && temp <= 15;     
            case 5 -> temp <= 10;                    
            default -> true;
        };

        // Проверка по сезону
        if (card.getSeason() != null) {
            boolean seasonMatch = switch (card.getSeason()) {
                case SUMMER -> temp > 15;
                case SPRING, AUTUMN -> temp >= 0 && temp <= 20;
                case WINTER -> temp < 10;
                default -> true;
            };
            return tempMatch && seasonMatch;
        }

        return tempMatch;
    }

    private String getCategoryDisplayName(ClothingCategory category) {
    return switch (category) {
        case TOP_BASE -> "Верх (футболка/рубашка)";
        case TOP_MID -> "Средний слой (свитер/кофта)";
        case TOP_OUTER -> "Верхняя одежда (куртка/пальто)";
        case BOTTOM -> "Низ (брюки/юбка/шорты)";
        case SHOES -> "Обувь";
        case HEAD -> "Головной убор";
        case ACCESSORY -> "Аксессуары";
    };
}
}