package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPerformanceSalesSnapshotDTO {
    private BigDecimal revenue;
    private Integer saleCount;
    private Integer unitsSold;
}
