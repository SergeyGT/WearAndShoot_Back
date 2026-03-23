package com.w_s_backend.w_s.Repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.w_s_backend.w_s.models.Outfit;

@Repository
public interface OutfitRepository extends JpaRepository<Outfit, Long>{
    List<Outfit> findByUserIdOrderByCreatedAtDesc(Long userId);
}
