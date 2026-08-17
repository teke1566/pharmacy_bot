package com.tenahub.bot.service;

import com.tenahub.bot.dto.MiniAppMedicinePhotosDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MiniAppPhotoDTO;
import org.springframework.web.multipart.MultipartFile;

public interface PharmacyMiniAppMediaService {

    MiniAppOperationResponseDTO submitLicenseUpdate(Long telegramId, MultipartFile licenseFile, String licenseExpiryDate);

    MiniAppPharmacyPhotosDTO listPharmacyPhotos(Long telegramId);

    MiniAppPhotoDTO addPharmacyPhoto(Long telegramId, MultipartFile file, String caption);

    MiniAppPhotoDTO setMainPharmacyPhoto(Long telegramId, Long photoId);

    void removePharmacyPhoto(Long telegramId, Long photoId);

    MiniAppMedicinePhotosDTO listMedicinePhotos(Long telegramId, Long itemId);

    MiniAppPhotoDTO addMedicinePhoto(Long telegramId, Long itemId, MultipartFile file, String caption);

    MiniAppPhotoDTO setMainMedicinePhoto(Long telegramId, Long itemId, Long photoId);

    void removeMedicinePhoto(Long telegramId, Long itemId, Long photoId);
}
