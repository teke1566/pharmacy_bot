package com.tenahub.bot.service;

import com.tenahub.bot.entity.PharmacyPhoto;

import java.util.List;
import java.util.Optional;

public interface PharmacyPhotoService {

    int MAX_PHOTOS = 4;

    List<PharmacyPhoto> listByPharmacyId(Long pharmacyId);

    PharmacyPhoto addPhoto(Long pharmacyId, String fileId, String caption);

    PharmacyPhoto setMainPhoto(Long pharmacyId, Long photoId);

    PharmacyPhoto removePhoto(Long pharmacyId, Long photoId);

    Optional<PharmacyPhoto> getMainPhoto(Long pharmacyId);

    void ensureLegacyPhotoImported(Long pharmacyId);

    List<String> getOrderedFileIds(Long pharmacyId);
}
