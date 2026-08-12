package com.tenahub.bot.service;

import com.tenahub.bot.dto.MiniAppMedicinePhotosDTO;
import com.tenahub.bot.dto.MiniAppAuthSendCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyDetailDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationPreloadResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationCreateRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationResponseDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;

import java.util.List;

/**
 * Service interface for Mini App API operations.
 * Currently provides read-only access to pharmacy photos.
 */
public interface MiniAppService {

    MiniAppOperationResponseDTO sendVerificationCode(MiniAppAuthSendCodeRequestDTO request);

    MiniAppAuthVerifyCodeResponseDTO verifyCode(MiniAppAuthVerifyCodeRequestDTO request);

    MiniAppReservationConfirmResponseDTO confirmReservation(MiniAppReservationConfirmRequestDTO request);

    List<MiniAppReservationCardDTO> getActiveReservations(Long telegramUserId);

    List<MiniAppReservationCardDTO> getReservationHistory(Long telegramUserId);

    MiniAppOperationResponseDTO hideReservationFromHistory(Long reservationId, Long telegramUserId);

    MiniAppOperationResponseDTO clearReservationHistory(Long telegramUserId);
    
    /**
     * Fetch photos for a specific pharmacy in Mini App format.
     * 
     * @param pharmacyId the ID of the pharmacy
     * @return MiniAppPharmacyPhotosDTO containing pharmacy details and ordered photos
     *         Photos list will be empty if pharmacy has no photos
     * @throws RuntimeException if pharmacy not found
     */
    MiniAppPharmacyPhotosDTO getPharmacyPhotos(Long pharmacyId);
    
    /**
     * Download a specific pharmacy photo as binary image data.
     * 
     * @param pharmacyId the pharmacy ID
     * @param photoId the photo ID
     * @return byte array containing the image data
     * @throws RuntimeException if pharmacy, photo, or file download fails
     */
    byte[] downloadPharmacyPhoto(Long pharmacyId, Long photoId);

    MiniAppMedicinePhotosDTO getMedicinePhotos(Long medicineId);

    byte[] downloadMedicinePhoto(Long medicineId, Long photoId);

    Long resolveMedicineId(Long pharmacyId, String medicineName);

    MiniAppOperationResponseDTO cancelReservation(Long reservationId, Long telegramUserId);

    MiniAppReservationPreloadResponseDTO getReservationPreload(Long pharmacyId, List<Long> medicineIds);

    List<PharmacyResponseDTO> search(String medicine,
                                     Double latitude,
                                     Double longitude,
                                     Long userId,
                                     String sort,
                                     String filter);

    MiniAppPharmacyDetailDTO getPharmacyDetails(Long pharmacyId);

    MiniAppReservationResponseDTO createReservation(MiniAppReservationCreateRequestDTO request);
}
