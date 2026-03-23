package com.w_s_backend.w_s.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "outfits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outfit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutfitStyle style;

    private String outfitName;          

    private double temperatureC;        
    private String weatherCondition;    

    @ManyToMany
    @JoinTable(
        name = "outfit_cloth_cards",
        joinColumns = @JoinColumn(name = "outfit_id"),
        inverseJoinColumns = @JoinColumn(name = "cloth_card_id")
    )
    @Builder.Default
    private List<ClothCard> items = new ArrayList<>();

    private String generatedImageUrl; 
}