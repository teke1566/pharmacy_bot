package com.tenahub.bot.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationCardDTO {
    private Long reservationId;
    private String reservationGroupId;
    private boolean grouped;
    private String groupedStatus;
    private boolean prescriptionRequired;
    private String prescriptionStatus;
    private String prescriptionReviewStatus;
    private String prescriptionStatusLabel;
    private String prescriptionRejectionReason;
    private String prescriptionClarificationMessage;
    private Long pharmacyId;
    private String pharmacyName;
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private String reservationStatus;
    private String status;
    private String reservationStatusLabel;
    private boolean readyForPickup;
    private boolean canShowQr;
    private boolean showQrCode;
    private String userFacingStage;
    private LocalDateTime holdUntil;
    private LocalDateTime expiresAt;
    private String qrToken;
    private LocalDateTime createdAt;
    private String phone;
    private List<MiniAppReservationConfirmItemResponseDTO> items;
}