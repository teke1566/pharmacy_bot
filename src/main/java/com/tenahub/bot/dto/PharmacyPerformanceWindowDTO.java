package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPerformanceWindowDTO {
    private Integer total;
    private Integer approved;
    private Integer fulfilled;
    private Integer rejected;
    private Integer expired;
    private Integer cancelled;
    private Double approvalRate;
    private Double fulfillmentRate;
    private Double avgResponseMinutes;
}
