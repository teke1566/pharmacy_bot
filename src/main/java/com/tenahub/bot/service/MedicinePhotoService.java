package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicinePhoto;

import java.util.List;
import java.util.Optional;

public interface MedicinePhotoService {

    int MAX_PHOTOS = 4;

    List<MedicinePhoto> listByMedicineId(Long medicineId);

    MedicinePhoto addPhoto(Long medicineId, String telegramFileId, String caption);

    MedicinePhoto setMainPhoto(Long medicineId, Long photoId);

    MedicinePhoto removePhoto(Long medicineId, Long photoId);

    Optional<MedicinePhoto> getMainPhoto(Long medicineId);

    List<String> getOrderedFileIds(Long medicineId);

    byte[] getImageBytesByTelegramFileId(String telegramFileId);
}