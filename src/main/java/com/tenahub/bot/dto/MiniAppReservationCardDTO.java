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
    private String prescriptionReviewStatus;
    private String prescriptionRejectionReason;
    private Long pharmacyId;
    private String pharmacyName;
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private String status;
    private LocalDateTime expiresAt;
    private String qrToken;
    private LocalDateTime createdAt;
    private String phone;
    private List<MiniAppReservationConfirmItemResponseDTO> items;
}