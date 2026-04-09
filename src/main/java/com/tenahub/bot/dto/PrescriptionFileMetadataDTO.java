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
public class PrescriptionFileMetadataDTO {
    private Long prescriptionId;
    private Long reservationId;
    private String reservationGroupId;
    private Long userId;
    private Long pharmacyId;
    private Long medicineId;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String rejectionReason;
}