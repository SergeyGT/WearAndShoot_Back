package com.w_s_backend.w_s.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
import com.w_s_backend.w_s.models.ColorScheme;
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
    private final ColorMatchingService colorMatchingService;
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
    public List<Outfit> generateAndSaveOutfits(Long userId, OutfitStyle style, int count, String customName, ColorScheme colorScheme) {
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
        ClothingCategory.TOP_BASE,
        ClothingCategory.BOTTOM
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

    // Получаем погоду
    CurrentWeatherDto weather = null;
    try {
        weather = weatherService.getCurrentWeather("Moscow");
    } catch (Exception e) {
        log.warn("Не удалось получить погоду, используем значения по умолчанию", e);
    }

    double temp = weather != null ? weather.getCurrent().getTemp_c() : 15.0;
    String condition = weather != null ? weather.getCurrent().getCondition().getText() : "Ясно";

    // Генерируем ВСЕ возможные комбинации
    List<Outfit> allPossibleOutfits = generateAllPossibleOutfits(
        user, cardsByCategory, style, temp, condition, colorScheme
    );

    if (allPossibleOutfits.isEmpty()) {
        throw new IllegalStateException(
            "Не удалось сгенерировать ни одного образа. Попробуйте добавить больше вещей в гардероб."
        );
    }

    // Перемешиваем и выбираем нужное количество
    Collections.shuffle(allPossibleOutfits, new Random());
    List<Outfit> selectedOutfits = allPossibleOutfits.stream()
        .limit(Math.min(count, allPossibleOutfits.size()))
        .collect(Collectors.toList());

    // Сохраняем выбранные образы
    List<Outfit> savedOutfits = new ArrayList<>();
    for (int i = 0; i < selectedOutfits.size(); i++) {
        Outfit outfit = selectedOutfits.get(i);
        
        // ИСПРАВЛЕНО: Используем кастомное имя или генерируем по умолчанию
        String outfitName;
        if (customName != null && !customName.trim().isEmpty()) {
            outfitName = selectedOutfits.size() > 1 
                ? customName.trim() + " #" + (i + 1) 
                : customName.trim();
        } else {
            outfitName = getStyleDisplayName(style) + " образ #" + (i + 1);
        }
        
        outfit.setOutfitName(outfitName);
        outfit.setUser(user);
        outfit.setStyle(style);
        outfit.setTemperatureC(temp);
        outfit.setWeatherCondition(condition);
        outfit.setIsLiked(false);
        outfit.setCreatedAt(LocalDateTime.now());
        
        outfitRepository.save(outfit);
        savedOutfits.add(outfit);
        
        log.info("Сохранен образ '{}': {} предметов", outfitName, outfit.getItems().size());
    }

    return savedOutfits;
    }

    // В конец класса ClothCardService добавляем:

@Transactional
public void deleteCard(Long cardId, Long userId) {
    ClothCard card = _clothCardPepository.findById(cardId)
        .orElseThrow(() -> new RuntimeException("Вещь не найдена"));
    
    // Проверяем, что вещь принадлежит пользователю
    if (!card.getUser().getId().equals(userId)) {
        throw new RuntimeException("Вы можете удалять только свои вещи");
    }
    
    // Удаляем связи с образами
    List<Outfit> outfitsWithCard = outfitRepository.findAllByItemsContaining(card);
    for (Outfit outfit : outfitsWithCard) {
        outfit.getItems().remove(card);
        // Если в образе осталось меньше 2 вещей — удаляем образ целиком
        if (outfit.getItems().size() < 2) {
            outfitRepository.delete(outfit);
        } else {
            outfitRepository.save(outfit);
        }
    }
    
    // Удаляем файл изображения
    if (card.getImagePath() != null && !card.getImagePath().isEmpty()) {
        deleteImagePath(card.getImagePath());
    }
    
    // Удаляем карточку
    _clothCardPepository.delete(card);
    
    log.info("Удалена вещь ID: {} пользователя ID: {}", cardId, userId);
}

@Transactional
public void deleteOutfit(Long outfitId, Long userId) {
    Outfit outfit = outfitRepository.findById(outfitId)
        .orElseThrow(() -> new RuntimeException("Образ не найден"));
    
    // Проверяем, что образ принадлежит пользователю
    if (!outfit.getUser().getId().equals(userId)) {
        throw new RuntimeException("Вы можете удалять только свои образы");
    }
    
    outfitRepository.delete(outfit);
    
    log.info("Удален образ ID: {} пользователя ID: {}", outfitId, userId);
}
    private String getStyleDisplayName(OutfitStyle style) {
        return switch (style) {
            case BUSINESS_CASUAL -> "Деловой";
            case SMART_CASUAL -> "Смарт-кэжуал";
            case STREETWEAR -> "Стритвир";
            case SPORTY -> "Спортивный";
            case ELEGANT -> "Элегантный";
            case CASUAL -> "Повседневный";
            case WINTER_CASUAL -> "Зимний";
            case SUMMER_VACATION -> "Летний отпуск";
            case OFFICE_FORMAL -> "Офисный";
        };
    }

   private List<Outfit> generateAllPossibleOutfits(
    User user,
    Map<ClothingCategory, List<ClothCard>> cardsByCategory,
    OutfitStyle style,
    double temp,
    String condition, 
    ColorScheme colorScheme
) {
    List<Outfit> allOutfits = new ArrayList<>();
    Set<String> usedCombinations = new HashSet<>();

    // Получаем кандидатов по категориям
    List<ClothCard> topBaseCards = filterCards(cardsByCategory, ClothingCategory.TOP_BASE, style, temp);
    List<ClothCard> bottomCards = filterCards(cardsByCategory, ClothingCategory.BOTTOM, style, temp);
    
    // Проверка обязательных категорий
    if (topBaseCards.isEmpty()) {
        throw new IllegalStateException(
            "Нет вещей стиля '" + getStyleDisplayName(style) + 
            "' в категории 'Верх'. Добавьте подходящие вещи или выберите другой стиль."
        );
    }
    if (bottomCards.isEmpty()) {
        throw new IllegalStateException(
            "Нет вещей стиля '" + getStyleDisplayName(style) + 
            "' в категории 'Низ'. Добавьте подходящие вещи или выберите другой стиль."
        );
    }

    // Опциональные категории
    List<ClothCard> shoesCards = filterCards(cardsByCategory, ClothingCategory.SHOES, style, temp);
    List<ClothCard> midCards = temp <= 15 ? filterCards(cardsByCategory, ClothingCategory.TOP_MID, style, temp) : Collections.emptyList();
    List<ClothCard> outerCards = temp <= 10 ? filterCards(cardsByCategory, ClothingCategory.TOP_OUTER, style, temp) : Collections.emptyList();
    List<ClothCard> headCards = temp <= 5 ? filterCards(cardsByCategory, ClothingCategory.HEAD, style, temp) : Collections.emptyList();
    List<ClothCard> accessoryCards = filterCards(cardsByCategory, ClothingCategory.ACCESSORY, style, temp);

    // Генерируем все комбинации
    for (ClothCard top : topBaseCards) {
        for (ClothCard bottom : bottomCards) {
            if (top.getId().equals(bottom.getId())) continue;
            
            // Проверка цвета
            List<String> baseColors = new ArrayList<>();
            baseColors.add(top.getColor());
            
            if (!colorMatchingService.matchesColorScheme(bottom.getColor(), baseColors, colorScheme)) {
                continue;
            }
            
            // Базовый набор (верх + низ)
            List<ClothCard> baseItems = new ArrayList<>();
            baseItems.add(top);
            baseItems.add(bottom);
            
            // ===== НАЧАЛО НОВОГО КОДА =====
            
            // Если холодно — обязательно добавляем mid и/или outer слои
            List<List<ClothCard>> layerCombinations = new ArrayList<>();
            layerCombinations.add(new ArrayList<>()); // вариант без доп.слоёв
            
            // Добавляем варианты со средним слоем
            for (ClothCard mid : midCards) {
                if (mid.getId().equals(top.getId()) || mid.getId().equals(bottom.getId())) continue;
                List<ClothCard> withMid = new ArrayList<>();
                withMid.add(mid);
                layerCombinations.add(withMid);
            }
            
            // Добавляем варианты с верхней одеждой
            for (ClothCard outer : outerCards) {
                if (outer.getId().equals(top.getId()) || outer.getId().equals(bottom.getId())) continue;
                List<ClothCard> withOuter = new ArrayList<>();
                withOuter.add(outer);
                layerCombinations.add(withOuter);
                
                // Комбинация mid + outer
                for (ClothCard mid : midCards) {
                    if (mid.getId().equals(outer.getId()) || 
                        mid.getId().equals(top.getId()) || 
                        mid.getId().equals(bottom.getId())) continue;
                    List<ClothCard> withMidOuter = new ArrayList<>();
                    withMidOuter.add(mid);
                    withMidOuter.add(outer);
                    layerCombinations.add(withMidOuter);
                }
            }
            
            // Для каждой комбинации слоёв создаём образы
            for (List<ClothCard> layers : layerCombinations) {
                List<ClothCard> itemsWithLayers = new ArrayList<>(baseItems);
                itemsWithLayers.addAll(layers);
                
                // Генерируем базовую комбинацию (без обуви)
                String baseKey = getCombinationKey(itemsWithLayers);
                if (!usedCombinations.contains(baseKey)) {
                    // Проверка цветов для доп.слоёв
                    boolean colorsMatch = true;
                    List<String> allColors = new ArrayList<>(baseColors);
                    allColors.add(bottom.getColor());
                    for (ClothCard layer : layers) {
                        if (!colorMatchingService.matchesColorScheme(layer.getColor(), allColors, colorScheme)) {
                            colorsMatch = false;
                            break;
                        }
                        allColors.add(layer.getColor());
                    }
                    
                    if (colorsMatch) {
                        Outfit baseOutfit = Outfit.builder()
                            .user(user)
                            .style(style)
                            .temperatureC(temp)
                            .weatherCondition(condition)
                            .items(new ArrayList<>(itemsWithLayers))
                            .isLiked(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                        
                        allOutfits.add(baseOutfit);
                        usedCombinations.add(baseKey);
                        
                        // Добавляем обувь
                        for (ClothCard shoes : shoesCards) {
                            boolean alreadyUsed = false;
                            for (ClothCard item : itemsWithLayers) {
                                if (item.getId().equals(shoes.getId())) {
                                    alreadyUsed = true;
                                    break;
                                }
                            }
                            if (alreadyUsed) continue;
                            
                            // Проверка цвета обуви
                            List<String> shoeColors = new ArrayList<>(allColors);
                            if (!colorMatchingService.matchesColorScheme(shoes.getColor(), shoeColors, colorScheme)) {
                                continue;
                            }
                            
                            List<ClothCard> withShoes = new ArrayList<>(itemsWithLayers);
                            withShoes.add(shoes);
                            
                            // Добавляем головной убор
                            for (ClothCard head : headCards) {
                                boolean headUsed = false;
                                for (ClothCard item : withShoes) {
                                    if (item.getId().equals(head.getId())) {
                                        headUsed = true;
                                        break;
                                    }
                                }
                                if (headUsed) continue;
                                
                                List<ClothCard> withHead = new ArrayList<>(withShoes);
                                withHead.add(head);
                                
                                String key = getCombinationKey(withHead);
                                if (!usedCombinations.contains(key)) {
                                    Outfit outfit = Outfit.builder()
                                        .user(user)
                                        .style(style)
                                        .temperatureC(temp)
                                        .weatherCondition(condition)
                                        .items(new ArrayList<>(withHead))
                                        .isLiked(false)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                    allOutfits.add(outfit);
                                    usedCombinations.add(key);
                                }
                            }
                            
                            // Вариант без головного убора
                            String shoesKey = getCombinationKey(withShoes);
                            if (!usedCombinations.contains(shoesKey)) {
                                Outfit shoesOutfit = Outfit.builder()
                                    .user(user)
                                    .style(style)
                                    .temperatureC(temp)
                                    .weatherCondition(condition)
                                    .items(new ArrayList<>(withShoes))
                                    .isLiked(false)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                                allOutfits.add(shoesOutfit);
                                usedCombinations.add(shoesKey);
                            }
                        }
                    }
                }
            }
            // ===== КОНЕЦ НОВОГО КОДА =====
        }
    }

    log.info("Сгенерировано {} уникальных комбинаций", allOutfits.size());
    return allOutfits;
}


    private boolean matchesStyle(ClothCard card, OutfitStyle outfitStyle) {
         if (card.getStyle() == null) return true;

    return switch (outfitStyle) {
        case BUSINESS_CASUAL, OFFICE_FORMAL -> 
            card.getStyle() == ClothStyle.BUSINESS || card.getStyle() == ClothStyle.OFFICE_FORMAL;
        
        case SMART_CASUAL -> 
            card.getStyle() == ClothStyle.CASUAL || card.getStyle() == ClothStyle.ELEGANT;
        
        case ELEGANT -> 
            card.getStyle() == ClothStyle.ELEGANT || card.getStyle() == ClothStyle.BUSINESS;
        
        case CASUAL -> 
            card.getStyle() == ClothStyle.CASUAL;
        
        case STREETWEAR -> 
            card.getStyle() == ClothStyle.CASUAL || card.getStyle() == ClothStyle.STREETWEAR;
        
        case SPORTY -> 
            card.getStyle() == ClothStyle.SPORT;
        
        case WINTER_CASUAL -> 
            card.getStyle() == ClothStyle.CASUAL || card.getStyle() == ClothStyle.WINTER_CASUAL;
        
        case SUMMER_VACATION -> 
            card.getStyle() == ClothStyle.CASUAL || card.getStyle() == ClothStyle.SUMMER_VACATION;
        
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
    }; }

    private List<ClothCard> filterCards(
    Map<ClothingCategory, List<ClothCard>> cardsByCategory,
    ClothingCategory category,
    OutfitStyle style,
    double temp
    ) {
        List<ClothCard> cards = cardsByCategory.getOrDefault(category, Collections.emptyList());
        if (cards.isEmpty()) return cards;
        
        return cards.stream()
            .filter(c -> matchesStyle(c, style))
            .filter(c -> matchesWeather(c, temp))
            .collect(Collectors.toList());
    }

    // Генерация уникального ключа для комбинации
    private String getCombinationKey(List<ClothCard> items) {
        return items.stream()
            .map(c -> c.getId().toString())
            .sorted()
            .collect(Collectors.joining("-"));
    }
}
