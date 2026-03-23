package com.tenahub.bot.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PharmacyResponseDTO {
    private Long id;
    private String name;
    private String area;
    private String phone;
    private double distance;
    private double latitude;
    private double longitude;

    private double score;
    private double rating;

    private boolean approved;
    private boolean canRate;

    private Integer stockQuantity;
    private boolean outOfStock;

    private String medicineName;
    private BigDecimal price;

    // add these for filters / display
    private boolean openNow;
    private String openTime;
    private String closeTime;
        private boolean favourite;   // add this

}