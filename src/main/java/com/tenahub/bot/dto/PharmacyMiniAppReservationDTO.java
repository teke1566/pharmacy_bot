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
public class PharmacyMiniAppReservationDTO {
    private Long reservationId;
    private String reservationGroupId;
    private String medicineName;
    private Integer quantity;
    private String status;
    private String customerName;
    private String customerPhone;
    private boolean prescriptionRequired;
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private String prescriptionClarificationMessage;
    private String rejectionReason;
    private java.util.List<String> prescriptionImages;
    private String qrToken;
    private Long fulfilledByTelegramId;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime fulfilledAt;
}
