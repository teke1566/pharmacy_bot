package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyMiniAppInventoryItemDTO {
    private Long itemId;
    private Long medicineId;
    private String medicineName;
    private Integer stockQuantity;
    private BigDecimal price;
    private String currency;
    private boolean requiresPrescription;
    private boolean inStock;
    private boolean outOfStock;
    private boolean lowStock;
    private Integer lowStockThreshold;
    private String batchNumber;
    private LocalDate expiryDate;
    private String strength;
    private String dosageForm;
    private boolean archived;
    private boolean expired;
    private boolean expiringSoon;
    private Integer lotCount;
    private LocalDateTime lastUpdatedAt;
}
