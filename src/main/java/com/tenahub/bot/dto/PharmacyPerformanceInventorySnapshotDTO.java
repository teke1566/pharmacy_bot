package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPerformanceInventorySnapshotDTO {
    private Integer totalItems;
    private Integer inStock;
    private Integer lowStock;
    private Integer outOfStock;
    private Integer updatedInPeriod;
}
