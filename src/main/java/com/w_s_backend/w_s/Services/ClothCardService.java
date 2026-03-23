package com.w_s_backend.w_s.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
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

    // @Value("${file.upload-dir}")
    // private String UPLOAD_DIR;
    private final String UPLOAD_DIR = "uploads/images/";

    public ClothCard createCard(ClothCardDTO  clothCardDTO, MultipartFile image){
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

            if(!oldPath.isEmpty()){
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
            return "";
        }
    }

    @Transactional
    public List<Outfit> generateAndSaveOutfits(Long userId, OutfitStyle style, int count) {
        if (count < 1 || count > 10) count = 1; // ограничим разумно

        User user = _userService.findById(userId);
        List<ClothCard> allCards = _clothCardPepository.findByUserId(userId);

        if (allCards.isEmpty()) {
            throw new IllegalStateException("У пользователя нет вещей для генерации образов");
        }

        List<Outfit> outfits = new ArrayList<>();

        String city = weatherService.getCurrentWeather("Moscow").getLocation().getCountry();
        CurrentWeatherDto weather = null;
        try {
            weather = weatherService.getCurrentWeather(city);
        } catch (Exception e) {
            log.warn("Не удалось получить погоду, используем дефолт", e);
        }

        double temp = weather != null ? weather.getCurrent().getTemp_c() : 10.0;
        String condition = weather != null ? weather.getCurrent().getCondition().getText() : "Облачно";

        for (int i = 1; i <= count; i++) {
            Outfit outfit = generateSingleOutfit(user, allCards, style, temp, condition, i);
            outfitRepository.save(outfit);
            outfits.add(outfit);
        }

        return outfits;
    }

    private Outfit generateSingleOutfit(
            User user,
            List<ClothCard> allCards,
            OutfitStyle style,
            double temp,
            String condition,
            int index
    ) {
        List<ClothCard> selected = new ArrayList<>();

        // Логика подбора в зависимости от стиля и погоды
        ClothingCategory[] required = getRequiredCategories(style, temp);

        for (ClothingCategory cat : required) {
            // Фильтруем подходящие вещи по категории и стилю
            List<ClothCard> candidates = allCards.stream()
                .filter(c -> c.getCategory() == cat)
                .filter(c -> matchesStyle(c, style))
                .filter(c -> matchesWeather(c, temp))
                .collect(Collectors.toList());

            if (!candidates.isEmpty()) {
                // Выбираем случайную или самую подходящую
                ClothCard chosen = candidates.get(new Random().nextInt(candidates.size()));
                selected.add(chosen);
            }
        }

        return Outfit.builder()
            .user(user)
            .style(style)
            .outfitName(style.name() + " образ #" + index)
            .temperatureC(temp)
            .weatherCondition(condition)
            .items(selected)
            .build();
    }
    private ClothingCategory[] getRequiredCategories(OutfitStyle style, double temp) {
        List<ClothingCategory> required = new ArrayList<>();

        required.add(ClothingCategory.TOP_BASE);   
        required.add(ClothingCategory.BOTTOM);    
        required.add(ClothingCategory.SHOES);      

        if (temp <= 15 || style == OutfitStyle.STREETWEAR || style == OutfitStyle.WINTER_CASUAL) {
            required.add(ClothingCategory.HEAD);
        }

        if (temp <= 18 || style == OutfitStyle.BUSINESS_CASUAL || style == OutfitStyle.OFFICE_FORMAL) {
            required.add(ClothingCategory.TOP_MID);
        }

        if (temp <= 10 || style == OutfitStyle.WINTER_CASUAL) {
            required.add(ClothingCategory.TOP_OUTER);
        }

        if (style == OutfitStyle.ELEGANT || style == OutfitStyle.BUSINESS_CASUAL ||
            (temp <= 5 && style == OutfitStyle.WINTER_CASUAL)) {
            required.add(ClothingCategory.ACCESSORY);
        }

        if (temp > 25) {
            required.remove(ClothingCategory.TOP_MID);
            required.remove(ClothingCategory.TOP_OUTER);
            required.remove(ClothingCategory.HEAD);
        }

        return required.toArray(new ClothingCategory[0]);
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

    private boolean matchesWeather(ClothCard card, double temp) {
        if (card.getWarmthLevel() == null) return true; 

        int warmth = card.getWarmthLevel();

        // Базовая логика по температуре
        boolean tempMatch = switch (warmth) {
            case 1 -> temp > 20;                    
            case 2 -> temp >= 15 && temp <= 25;
            case 3 -> temp >= 5  && temp <= 20;
            case 4 -> temp >= -5 && temp <= 10;
            case 5 -> temp <= 5;                   
            default -> true;
        };

        return tempMatch;
    }
}
