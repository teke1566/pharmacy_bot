package com.tenahub.bot.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationPreloadItemDTO {
    private Long medicineId;
    private String medicineName;
    private BigDecimal price;
    private Integer stockQuantity;
    private boolean requiresPrescription;
    private String currency;
}