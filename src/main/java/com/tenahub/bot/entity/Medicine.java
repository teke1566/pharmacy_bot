package com.tenahub.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "medicines")
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "canonical_name", nullable = false, unique = true)
    private String canonicalName;

    @Column(name = "active_ingredient")
    private String activeIngredient;

    private String strength;

    @Column(name = "dosage_form")
    private String dosageForm;

    private String manufacturer;

    private String category;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Builder.Default
    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired = false;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
