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
public class PrescriptionStatusItemDTO {
    private Long reservationId;
    private String reservationGroupId;
    private Long pharmacyId;
    private Long medicineId;
    private String medicineName;
    private Integer quantity;
    private boolean prescriptionRequired;
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String rejectionReason;
}