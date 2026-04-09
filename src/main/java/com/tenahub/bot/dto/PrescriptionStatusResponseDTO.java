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
    private boolean prescriptionRequired;
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String rejectionReason;
    private List<PrescriptionStatusItemDTO> items;
    private List<PrescriptionFileMetadataDTO> files;
}