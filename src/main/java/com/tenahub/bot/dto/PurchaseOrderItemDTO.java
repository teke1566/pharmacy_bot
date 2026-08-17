package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemDTO {
    private Long itemId;
    private String medicineName;
    private Integer quantityOrdered;
    private Integer quantityReceived;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private String batchNumber;
    private LocalDate expiryDate;
}
