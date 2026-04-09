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
public class MiniAppReservationResponseDTO {
    private Long reservationId;
    private Long pharmacyId;
    private Long userId;
    private String medicineName;
    private Integer requestedQuantity;
    private String status;
    private boolean prescriptionRequired;
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String customerPhone;
    private String customerName;
}
