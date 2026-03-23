package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_search_logs")
@Getter
@Setter
public class MedicineSearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt = LocalDateTime.now();
}