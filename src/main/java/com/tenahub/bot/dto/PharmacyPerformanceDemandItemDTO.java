package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPerformanceDemandItemDTO {
    private String medicineName;
    private Integer searchCount;
    private Integer stockQuantity;
    private Boolean outOfStock;
    private Boolean lowStock;
    private String demandLabel;
}
