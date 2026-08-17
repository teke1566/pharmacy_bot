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
public class PharmacyPricingItemDTO {
    private Long itemId;
    private String medicineName;
    private BigDecimal sellingPrice;
    private BigDecimal purchaseCost;
    private BigDecimal grossProfit;
    private BigDecimal grossMarginPercent;
    private BigDecimal markupPercent;
    private String currency;
    private Integer stockQuantity;
    private BigDecimal inventoryCostValue;
    private BigDecimal potentialSalesValue;
    private BigDecimal potentialGrossProfit;
    private boolean belowCost;
    private Long version;
    private LocalDateTime updatedAt;
    private BigDecimal promotionalPrice;
    private String activePromotionLabel;
}
