package com.tenahub.bot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private boolean verified;
    private boolean canRate;

    private Integer stockQuantity;
    private boolean outOfStock;

    private String medicineName;
    private Long medicineId;
    private BigDecimal price;
    private boolean requiresPrescription;

    // add these for filters / display
    private boolean openNow;
    private String openTime;
    private String closeTime;
    private boolean temporarilyClosed;
    private String temporaryClosureReason;
    private LocalDateTime temporaryClosedUntil;
    private boolean favourite;

}