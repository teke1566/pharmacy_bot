package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private LocalDateTime lastUpdatedAt;
}
