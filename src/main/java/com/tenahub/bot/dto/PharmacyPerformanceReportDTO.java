package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyPerformanceReportDTO {
    private String period;
    private LocalDateTime from;
    private LocalDateTime to;
    private Integer healthScore;
    private String healthGrade;
    private List<PharmacyHealthFactorDTO> healthFactors;
    private PharmacyPerformanceWindowDTO reservations;
    private PharmacyPerformanceInventorySnapshotDTO inventory;
    private PharmacyPerformanceSalesSnapshotDTO sales;
    private List<PharmacyPerformanceDemandItemDTO> topDemand;
    private Integer criticalRestockCount;
}
