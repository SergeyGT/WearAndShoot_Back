package com.w_s_backend.w_s.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.w_s_backend.w_s.DTOs.ClothCardDTO;
import com.w_s_backend.w_s.Repositories.ClothCardPepository;
import com.w_s_backend.w_s.models.ClothCard;
import com.w_s_backend.w_s.models.User;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ClothCardService {
    private final ClothCardPepository _clothCardPepository;
    private final UserService _userService;

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
}
