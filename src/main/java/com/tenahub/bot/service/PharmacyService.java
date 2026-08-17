package com.tenahub.bot.service;

import com.tenahub.bot.dto.MedicineSuggestionResult;
import com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import java.time.LocalDate;
import java.util.List;

public interface PharmacyService {

    List<PharmacyResponseDTO> searchMedicine(String medicine);

    List<PharmacyResponseDTO> searchMedicine(String medicine, Long catalogMedicineId);

    List<PharmacyResponseDTO> searchMedicineWithArea(String medicine, String area);

    List<PharmacyResponseDTO> searchMedicineWithCity(String medicine, String city);

    List<String> suggestMedicines(String input);

    MedicineSuggestionResult suggestMedicineOptions(String input);

    List<PharmacyResponseDTO> searchMedicineNearby(
            String medicine,
            double userLat,
            double userLon,
            Long userId
    );

    List<PharmacyResponseDTO> searchMedicineNearby(
            String medicine,
            Long catalogMedicineId,
            double userLat,
            double userLon,
            Long userId
    );

    List<PharmacyResponseDTO> listNearbyApproved(Double userLat, Double userLon);

    boolean isRegisteredPharmacy(Long telegramId);

    void updatePhone(Long telegramId, String phone);

    void updateMedicines(Long telegramId, String medicines);

    void updateHours(Long telegramId, String openTime, String closeTime);

    void updateLocation(Long telegramId, Double latitude, Double longitude, String city, String area,
                        String formattedAddress, String landmark);

    void updateLicense(Long telegramId, String fileId);

    void setTemporaryClosure(Long telegramId, String reason, int durationHours);

    void clearTemporaryClosure(Long telegramId);

        void savePendingLicenseUpdate(Long telegramId, String fileId, LocalDate expiryDate);

    void approvePendingLicenseUpdate(Long telegramId);

    void rejectPendingLicenseUpdate(Long telegramId);
    List<MultiMedicinePharmacyResultDTO> searchMultipleMedicinesNearby(
        List<String> medicines,
        double userLat,
        double userLon,
        Long userId
);
List<String> suggestAlternativeMedicines(String medicine);

boolean medicineExistsInCatalog(String medicine);
}
