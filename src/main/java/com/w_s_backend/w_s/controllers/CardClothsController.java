package com.w_s_backend.w_s.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.DTOs.ClothCardResponseDTO;
import com.w_s_backend.w_s.DTOs.OutfitGenerateRequest;
import com.w_s_backend.w_s.DTOs.OutfitResponse;
import com.w_s_backend.w_s.Services.ClothCardService;
import com.w_s_backend.w_s.Services.JwtService;
import com.w_s_backend.w_s.models.ClothCard;
import com.w_s_backend.w_s.models.Outfit;
import com.w_s_backend.w_s.models.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;





@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/cloth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CardClothsController {
    @Autowired
    private  ClothCardService clothCardService;

    @Autowired
    private JwtService jwtService;
    
    @PostMapping("/create")
    public ResponseEntity<ClothCardResponseDTO> createCard(
            @RequestPart("clothData") ClothCardDTO clothCardDTO,
            @RequestPart("image") MultipartFile image) 
    {

        ClothCard createCard = clothCardService.createCard(clothCardDTO, image);
        
        ClothCardResponseDTO clothCardResponseDTO = new ClothCardResponseDTO(
            createCard.getId());
        return ResponseEntity.ok(clothCardResponseDTO);
    }
    
    @PostMapping("/generate-outfits")
public ResponseEntity<?> generateOutfits(
        @RequestBody OutfitGenerateRequest request, HttpServletRequest httpRequest) {
    
      log.info("=== ЗАПРОС НА ГЕНЕРАЦИЮ ===" );
    log.info("Сессия: {}", httpRequest.getSession(false));
    log.info("Куки: {}", Arrays.toString(httpRequest.getCookies()));
    
         
    // Получаем userId из JWT токена
    Long userId = extractUserIdFromRequest(httpRequest);
    
    if (userId == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Не авторизован"));
    }
    
    log.info("Запрос на генерацию образов для userId: {}", userId);
    
    try {
        List<Outfit> outfits = clothCardService.generateAndSaveOutfits(
            userId,
            request.getStyle(),
            request.getCount(),
            request.getOutfitName()  // ДОБАВЛЕНО
        );
        
        List<OutfitResponse> responses = outfits.stream()
            .map(this::mapToOutfitResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
        
    } catch (IllegalStateException e) {
        log.warn("Ошибка генерации: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
        log.error("Неожиданная ошибка", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Внутренняя ошибка сервера"));
    }
}

// Добавь этот метод для извлечения userId из JWT
private Long extractUserIdFromRequest(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName())) {
                String token = cookie.getValue();
                if (jwtService.isTokenValid(token)) {
                    return jwtService.extractUserId(token);
                }
            }
        }
    }
    return null;
}

    @GetMapping("/userCards/{id}")
    public List<ClothCard> read(@PathVariable Long id) {
        return clothCardService.readAllCards(id);
    }

    @PutMapping("/edit/{cardId}")
    public ResponseEntity<ClothCardResponseDTO> editCard(
        @PathVariable Long cardId,
        @RequestPart("clothData") ClothCardDTO clothCardDTO,
        @RequestPart(value = "image", required = false) MultipartFile image) 
    {
        
        ClothCard createCard = clothCardService.updateCard(cardId, clothCardDTO, image);
        
        ClothCardResponseDTO clothCardResponseDTO = new ClothCardResponseDTO(
            createCard.getId());
        return ResponseEntity.ok(clothCardResponseDTO);
    }

    @GetMapping("/image/{cardId}")
    public ResponseEntity<byte[]> getImage(@PathVariable Long cardId) {
        try {
            log.info("Запрос изображения для карточки ID: {}", cardId);
            
            ClothCard card = clothCardService.getCardById(cardId);
            String imagePath = card.getImagePath();
            
            log.info("Путь к изображению из БД: {}", imagePath);
            
            if (imagePath != null && !imagePath.isEmpty()) {
                Path path = null;
                
                path = Paths.get(imagePath);
                
                if (!Files.exists(path)) {
                    String fileName = Paths.get(imagePath).getFileName().toString();
                    String alternativePath = "uploads/images/user_" + card.getUser().getId() + "/" + fileName;
                    path = Paths.get(alternativePath);
                    log.info("Пробуем альтернативный путь: {}", alternativePath);
                }
                
                if (!Files.exists(path)) {
                    String absolutePath = System.getProperty("user.dir") + "/" + imagePath;
                    path = Paths.get(absolutePath);
                    log.info("Пробуем абсолютный путь: {}", absolutePath);
                }
                
                log.info("Финальный путь: {}, существует: {}", path, Files.exists(path));
                
                if (Files.exists(path)) {
                    byte[] image = Files.readAllBytes(path);
                    
                    String contentType = "image/jpeg";
                    String fileName = path.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".png")) {
                        contentType = "image/png";
                    } else if (fileName.endsWith(".gif")) {
                        contentType = "image/gif";
                    } else if (fileName.endsWith(".webp")) {
                        contentType = "image/webp";
                    }
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(contentType));
                    headers.setContentLength(image.length);
                    
                    log.info("Изображение успешно загружено, размер: {} байт", image.length);
                    
                    return new ResponseEntity<>(image, headers, HttpStatus.OK);
                } else {
                    log.warn("Файл изображения не найден по пути: {}", path);
                }
            } else {
                log.warn("У карточки {} нет пути к изображению", cardId);
            }
        } catch (Exception e) {
            log.error("Ошибка при загрузке изображения для карточки {}: {}", cardId, e.getMessage(), e);
        }
        
        return ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/delete/{cardId}")
    public ResponseEntity<?> deleteCard(
            @PathVariable Long cardId,
            HttpServletRequest httpRequest) {
        
        Long userId = extractUserIdFromRequest(httpRequest);
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Не авторизован"));
        }
        
        try {
            clothCardService.deleteCard(cardId, userId);
            return ResponseEntity.ok(Map.of("message", "Вещь успешно удалена"));
        } catch (RuntimeException e) {
            log.warn("Ошибка удаления вещи: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Неожиданная ошибка при удалении вещи", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Внутренняя ошибка сервера"));
        }
    }

    @DeleteMapping("/outfits/{outfitId}")
    public ResponseEntity<?> deleteOutfit(
            @PathVariable Long outfitId,
            HttpServletRequest httpRequest) {
        
        Long userId = extractUserIdFromRequest(httpRequest);
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Не авторизован"));
        }
        
        try {
            clothCardService.deleteOutfit(outfitId, userId);
            return ResponseEntity.ok(Map.of("message", "Образ успешно удален"));
        } catch (RuntimeException e) {
            log.warn("Ошибка удаления образа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Неожиданная ошибка при удалении образа", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Внутренняя ошибка сервера"));
        }
    }                       
    @PostMapping("/outfits/{outfitId}/like")
    public ResponseEntity<OutfitResponse> toggleLikeOutfit(
            @PathVariable Long outfitId,
            @RequestBody Map<String, Long> request) {
        
        Long userId = request.get("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            Outfit outfit = clothCardService.toggleLikeOutfit(outfitId, userId);
            
            OutfitResponse resp = mapToOutfitResponse(outfit);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Ошибка при лайке образа: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }              
    @GetMapping("/outfits/liked/{userId}")
    public ResponseEntity<List<OutfitResponse>> getLikedOutfits(@PathVariable Long userId) {
        try {
            List<Outfit> likedOutfits = clothCardService.getLikedOutfits(userId);
            
            List<OutfitResponse> responses = likedOutfits.stream()
                .map(this::mapToOutfitResponse)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Ошибка при получении лайкнутых образов: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }             
    
    @GetMapping("/outfits/user/{userId}")
    public ResponseEntity<List<OutfitResponse>> getUserOutfits(@PathVariable Long userId) {
        try {
            List<Outfit> outfits = clothCardService.getUserOutfits(userId);
            
            List<OutfitResponse> responses = outfits.stream()
                .map(this::mapToOutfitResponse)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Ошибка при получении образов пользователя: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }   

    private OutfitResponse mapToOutfitResponse(Outfit outfit) {
    OutfitResponse resp = new OutfitResponse();
    resp.setId(outfit.getId());
    resp.setOutfitName(outfit.getOutfitName());
    resp.setStyle(outfit.getStyle());
    resp.setTemperatureC(outfit.getTemperatureC());
    resp.setWeatherCondition(outfit.getWeatherCondition());
    resp.setIsLiked(outfit.getIsLiked());
    resp.setCreatedAt(outfit.getCreatedAt());
    
    List<OutfitResponse.ClothCardShortDto> items = outfit.getItems().stream()
        .map(c -> {
            OutfitResponse.ClothCardShortDto dto = new OutfitResponse.ClothCardShortDto();
            dto.setId(c.getId());
            dto.setClothName(c.getClothName());
            dto.setImagePath(c.getImagePath());
            dto.setCategory(c.getCategory());
            return dto;
        }).collect(Collectors.toList());
    
    resp.setItems(items);
    return resp;
}
}
