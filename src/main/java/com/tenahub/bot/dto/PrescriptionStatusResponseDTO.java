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
public class PrescriptionStatusResponseDTO {
    private Long reservationId;
    private String reservationGroupId;
    private Long userId;
    private Long pharmacyId;
    private String customerPhone;
    private String customerName;
    private String note;
    private boolean prescriptionRequired;
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String rejectionReason;
    private String clarificationMessage;
    private List<PrescriptionStatusItemDTO> items;
    private List<PrescriptionFileMetadataDTO> files;

    // Additional fields for complete status refresh
    private String reservationStatus;
    private String pharmacyName;
    private String medicineName;
    private Integer quantity;
    private LocalDateTime expiresAt;
    private boolean canShowQr;
    private String userFacingStage;
}