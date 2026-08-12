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
public class MiniAppReservationConfirmItemResponseDTO {
    private Long reservationId;
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private String status;
    private boolean prescriptionRequired;
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private LocalDateTime expiresAt;
    private String qrToken;
}