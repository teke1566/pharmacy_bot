package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPricingOverviewDTO {
    private BigDecimal averageMarginPercent;
    private long pendingApprovals;
    private long activePromotions;
    private long scheduledChanges;
    private long priceChangesThisMonth;
    private long pricedItems;
    private long lowMarginCount;
    private List<PharmacyPricingItemDTO> lowMarginItems;
    private List<PharmacyPricingItemDTO> highMarginItems;
    private List<PriceHistoryDTO> recentChanges;
}
