package com.w_s_backend.w_s.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.DTOs.ClothCardResponseDTO;
import com.w_s_backend.w_s.DTOs.OutfitGenerateRequest;
import com.w_s_backend.w_s.DTOs.OutfitResponse;
import com.w_s_backend.w_s.Services.ClothCardService;
import com.w_s_backend.w_s.models.ClothCard;
import com.w_s_backend.w_s.models.Outfit;
import com.w_s_backend.w_s.models.User;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;





@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/cloth")
public class CardClothsController {
    @Autowired
    private  ClothCardService clothCardService;
    
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
    public ResponseEntity<List<OutfitResponse>> generateOutfits(
            @RequestBody OutfitGenerateRequest request,
            @AuthenticationPrincipal User user) {

        if (user == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }        
    
        Long userId = user.getId();

        List<Outfit> outfits = clothCardService.generateAndSaveOutfits(
            userId,
            request.getStyle(),
            request.getCount()
        );

        List<OutfitResponse> responses = outfits.stream().map(o -> {
            OutfitResponse resp = new OutfitResponse();
            resp.setId(o.getId());
            resp.setOutfitName(o.getOutfitName());
            resp.setStyle(o.getStyle());
            resp.setTemperatureC(o.getTemperatureC());
            resp.setWeatherCondition(o.getWeatherCondition());

            List<OutfitResponse.ClothCardShortDto> items = o.getItems().stream()
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
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
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
    
}
