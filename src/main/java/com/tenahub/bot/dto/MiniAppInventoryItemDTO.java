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
public class MiniAppInventoryItemDTO {
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private boolean outOfStock;
    private boolean requiresPrescription;
    private BigDecimal price;
    private String currency;
}
