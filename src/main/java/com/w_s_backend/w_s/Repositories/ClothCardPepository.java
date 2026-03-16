package com.w_s_backend.w_s.Repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.w_s_backend.w_s.models.ClothCard;

public interface ClothCardPepository extends JpaRepository<ClothCard, Long> {
    List<ClothCard> findByUserId(Long userId);
    Optional<ClothCard> findById(Long id);
}
