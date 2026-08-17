package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDTO {
    private Long movementId;
    private Long itemId;
    private Long batchId;
    private String batchNumber;
    private String medicineName;
    private String movementType;
    private Integer quantityChange;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private Long actorTelegramId;
    private String reason;
    private Long reservationId;
    private LocalDateTime createdAt;
}
