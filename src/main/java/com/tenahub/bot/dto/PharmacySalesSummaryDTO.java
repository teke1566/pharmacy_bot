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
public class PharmacySalesSummaryDTO {
    private String period;
    private BigDecimal revenue;
    private Integer saleCount;
    private Integer medicinesDispensed;
    private List<PharmacySaleItemDTO> topMedicines;
}
