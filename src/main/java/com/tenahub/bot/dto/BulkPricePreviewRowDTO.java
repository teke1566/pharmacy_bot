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
public class BulkPricePreviewRowDTO {
    private Long itemId;
    private String medicineName;
    private BigDecimal currentPrice;
    private BigDecimal newPrice;
    private BigDecimal percentChange;
    private BigDecimal marginAfter;
    private boolean belowCost;
}
