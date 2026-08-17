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
public class PriceHistoryDTO {
    private Long historyId;
    private Long itemId;
    private String medicineName;
    private BigDecimal oldSellingPrice;
    private BigDecimal newSellingPrice;
    private BigDecimal oldPurchaseCost;
    private BigDecimal newPurchaseCost;
    private String currency;
    private String reason;
    private String actorName;
    private Long actorStaffId;
    private Long requestId;
    private LocalDateTime effectiveAt;
    private LocalDateTime createdAt;
}
