package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationConfirmResponseDTO {
    private Long reservationId;
    private String reservationGroupId;
    private boolean grouped;
    private String groupedStatus;
    private String status;
    private boolean prescriptionRequired;
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private LocalDateTime expiresAt;
    private String qrToken;
    private String pharmacyName;
    private String medicineName;
    private Integer quantity;
    private List<MiniAppReservationConfirmItemResponseDTO> items;
}