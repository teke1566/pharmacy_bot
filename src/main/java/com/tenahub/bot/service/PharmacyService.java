package com.tenahub.bot.service;

import com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import java.util.List;

public interface PharmacyService {

    List<PharmacyResponseDTO> searchMedicine(String medicine);

    List<PharmacyResponseDTO> searchMedicineWithArea(String medicine, String area);

    List<PharmacyResponseDTO> searchMedicineWithCity(String medicine, String city);

    List<String> suggestMedicines(String input);

    List<PharmacyResponseDTO> searchMedicineNearby(
            String medicine,
            double userLat,
            double userLon,
            Long userId
    );

    boolean isRegisteredPharmacy(Long telegramId);

    void updatePhone(Long telegramId, String phone);

    void updateMedicines(Long telegramId, String medicines);

    void updateHours(Long telegramId, String openTime, String closeTime);

    void updateLicense(Long telegramId, String fileId);

    void savePendingLicenseUpdate(Long telegramId, String fileId);

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
