package com.w_s_backend.w_s.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.DTOs.ClothCardResponseDTO;
import com.w_s_backend.w_s.Services.ClothCardService;
import com.w_s_backend.w_s.models.ClothCard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;



@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/cloth")
public class CardClothsController {
    @Autowired
    private final ClothCardService clothCardService;

    
    @PostMapping
    public ResponseEntity<ClothCardResponseDTO> postMethodName(
            @RequestPart("clothData") ClothCardDTO clothCardDTO,
            @RequestPart("image") MultipartFile image) 
    {

        ClothCard createCard = clothCardService.createCard(clothCardDTO, image);
        
        ClothCardResponseDTO clothCardResponseDTO = new ClothCardResponseDTO(
            createCard.getId());
        return ResponseEntity.ok(clothCardResponseDTO);
    }
    

    @GetMapping("/userCards/{id}")
    public List<ClothCard> read(@PathVariable("id") Long id) {
        return clothCardService.readAllCards(id);
    }
    
}
