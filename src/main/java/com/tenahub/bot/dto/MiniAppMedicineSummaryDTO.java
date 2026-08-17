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
public class MiniAppMedicineSummaryDTO {
    private Long medicineId;
    private String medicineName;
    private String manufacturer;
    private String strength;
    private String dosageForm;
    private boolean prescriptionRequired;
    private BigDecimal price;
    private int availablePharmacies;
    private boolean outOfStock;
    private String imageUrl;
}
