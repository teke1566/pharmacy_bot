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
public class PriceChangeSubmitRequestDTO {
    private BigDecimal proposedSellingPrice;
    private BigDecimal purchaseCost;
    private String reason;
    private LocalDateTime effectiveAt;
    private Long expectedVersion;
    private boolean forceBelowCost;
    private boolean submitForApproval;
}
