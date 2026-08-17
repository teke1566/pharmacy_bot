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
public class PharmacyMiniAppInventoryPatchRequest {
    private Integer quantity;
    private BigDecimal price;
    private Boolean requiresPrescription;
    private Boolean available;
    private Integer lowStockThreshold;
    private String batchNumber;
    private LocalDate expiryDate;
    private boolean clearExpiry;
    private String strength;
    private String dosageForm;
    private Boolean archived;
    private String reason;
}
