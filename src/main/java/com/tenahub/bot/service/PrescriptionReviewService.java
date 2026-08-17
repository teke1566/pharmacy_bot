package com.tenahub.bot.service;

import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PrescriptionReviewService {

    PrescriptionStatusResponseDTO uploadPrescriptionFiles(Long reservationId,
                                                          String reservationGroupId,
                                                          Long userId,
                                                          Long pharmacyId,
                                                          Long medicineId,
                                                          String note,
                                                          List<MultipartFile> files);

    PrescriptionStatusResponseDTO getPrescriptionStatus(Long reservationId,
                                                        String reservationGroupId,
                                                        Long userId);

    PrescriptionStatusResponseDTO reviewPrescription(Long reservationId,
                                                     String reservationGroupId,
                                                     Long pharmacyTelegramId,
                                                     PrescriptionReviewRequestDTO request);

    void notifyCustomerOfPrescriptionDecision(PrescriptionStatusResponseDTO status, boolean approved);

    PrescriptionFileContent downloadPrescriptionFile(Long prescriptionId, Long pharmacyTelegramId);

    record PrescriptionFileContent(byte[] fileData, String contentType, String originalFilename) {
    }
}