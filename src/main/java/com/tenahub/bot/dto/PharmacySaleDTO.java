package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySaleDTO {
    private Long saleId;
    private Long reservationId;
    private String customerName;
    private Long actorTelegramId;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime createdAt;
    private List<PharmacySaleItemDTO> items;
}
