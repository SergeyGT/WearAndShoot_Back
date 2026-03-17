package com.w_s_backend.w_s.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.DTOs.ClothCardResponseDTO;
import com.w_s_backend.w_s.Services.ClothCardService;
import com.w_s_backend.w_s.models.ClothCard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;





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
                // Пробуем разные варианты пути
                Path path = null;
                
                // Вариант 1: как есть
                path = Paths.get(imagePath);
                
                // Вариант 2: если путь относительный, пробуем от корня проекта
                if (!Files.exists(path)) {
                    String fileName = Paths.get(imagePath).getFileName().toString();
                    String alternativePath = "uploads/images/user_" + card.getUser().getId() + "/" + fileName;
                    path = Paths.get(alternativePath);
                    log.info("Пробуем альтернативный путь: {}", alternativePath);
                }
                
                // Вариант 3: пробуем абсолютный путь
                if (!Files.exists(path)) {
                    String absolutePath = System.getProperty("user.dir") + "/" + imagePath;
                    path = Paths.get(absolutePath);
                    log.info("Пробуем абсолютный путь: {}", absolutePath);
                }
                
                log.info("Финальный путь: {}, существует: {}", path, Files.exists(path));
                
                if (Files.exists(path)) {
                    byte[] image = Files.readAllBytes(path);
                    
                    // Определяем тип изображения
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
        
        // Возвращаем заглушку если изображение не найдено
        return ResponseEntity.notFound().build();
    }
    
}
