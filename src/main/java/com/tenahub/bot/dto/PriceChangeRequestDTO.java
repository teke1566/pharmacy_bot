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
public class PriceChangeRequestDTO {
    private Long requestId;
    private Long itemId;
    private String medicineName;
    private BigDecimal currentSellingPrice;
    private BigDecimal proposedSellingPrice;
    private BigDecimal purchaseCostRef;
    private BigDecimal marginBefore;
    private BigDecimal marginAfter;
    private BigDecimal percentChange;
    private String currency;
    private LocalDateTime effectiveAt;
    private String reason;
    private String status;
    private Long requestedByStaffId;
    private Long approvedByStaffId;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private boolean requiresApproval;
}
