package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyMiniAppAddStockRequest {
    private String medicineName;
    private Integer quantity;
    private BigDecimal price;
    private Integer lowStockThreshold;
    private Boolean requiresPrescription;
    private String batchNumber;
    private LocalDate expiryDate;
    private String strength;
    private String dosageForm;
    private String manufacturer;
}
