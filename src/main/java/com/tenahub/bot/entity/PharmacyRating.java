package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class PharmacyRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private Long userId;

    private int rating; // 1-5

    private LocalDateTime createdAt;
}