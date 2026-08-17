package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyReservationScanResponseDTO {

    private boolean valid;
    private boolean grouped;
    private Long reservationId;
    private String reservationGroupId;
    private String status;
    private boolean canFulfill;
    private boolean prescriptionRequired;
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private Long pharmacyId;
    private String pharmacyName;
    private String medicineName;
    private Integer quantity;
    private String phone;
    private String qrToken;
    private Long scannedByTelegramId;
    private Long fulfilledByTelegramId;
    private LocalDateTime expiresAt;
    private List<MiniAppReservationConfirmItemResponseDTO> items;
}