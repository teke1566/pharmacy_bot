package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineBatchDTO {
    private Long batchId;
    private Long itemId;
    private String medicineName;
    private String batchNumber;
    private Integer quantity;
    private LocalDate expiryDate;
    private Long daysRemaining;
    private String supplier;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private LocalDateTime receivedAt;
    private boolean expired;
    private boolean expiringSoon;
    private String warning;
}
