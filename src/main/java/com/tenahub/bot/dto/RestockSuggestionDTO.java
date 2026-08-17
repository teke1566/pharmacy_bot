package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestockSuggestionDTO {
    private String medicineName;
    private Integer currentStock;
    private Integer recommendedQuantity;
    private Integer weeklySearches;
    private Integer reservationFailures;
    private Integer stockouts;
    private Integer score;
    private String priority;
    private String status;
    private String reason;
    private String demand;
    private String demandLabel;
}
