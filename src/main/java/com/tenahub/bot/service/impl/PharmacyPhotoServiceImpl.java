package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyPhoto;
import com.tenahub.bot.repository.PharmacyPhotoRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PharmacyPhotoServiceImpl implements PharmacyPhotoService {

    private final PharmacyPhotoRepository pharmacyPhotoRepository;
    private final PharmacyRepository pharmacyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyPhoto> listByPharmacyId(Long pharmacyId) {
        return pharmacyPhotoRepository.findByPharmacyIdOrderByMainPhotoDescSortOrderAscIdAsc(pharmacyId);
    }

    @Override
    @Transactional
    public PharmacyPhoto addPhoto(Long pharmacyId, String fileId, String caption) {
        if (fileId == null || fileId.isBlank()) {
            throw new RuntimeException("Photo file id is required");
        }

        ensureLegacyPhotoImported(pharmacyId);

        List<PharmacyPhoto> current = listByPharmacyId(pharmacyId);
        if (current.size() >= MAX_PHOTOS) {
            throw new RuntimeException("Maximum 4 photos allowed");
        }

        int nextSort = current.stream()
                .map(PharmacyPhoto::getSortOrder)
                .filter(v -> v != null)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        PharmacyPhoto saved = pharmacyPhotoRepository.save(
                PharmacyPhoto.builder()
                        .pharmacyId(pharmacyId)
                        .fileId(fileId)
                        .caption(caption)
                        .sortOrder(nextSort)
                        .mainPhoto(current.isEmpty())
                        .build()
        );

        syncLegacyMainPhoto(pharmacyId);
        return saved;
    }

    @Override
    @Transactional
    public PharmacyPhoto setMainPhoto(Long pharmacyId, Long photoId) {
        List<PharmacyPhoto> current = listByPharmacyId(pharmacyId);
        if (current.isEmpty()) {
            throw new RuntimeException("No photos found");
        }

        PharmacyPhoto target = current.stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        for (PharmacyPhoto photo : current) {
            photo.setMainPhoto(photo.getId().equals(photoId));
            pharmacyPhotoRepository.save(photo);
        }

        syncLegacyMainPhoto(pharmacyId);
        return target;
    }

    @Override
    @Transactional
    public PharmacyPhoto removePhoto(Long pharmacyId, Long photoId) {
        List<PharmacyPhoto> current = listByPharmacyId(pharmacyId);
        PharmacyPhoto target = current.stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        boolean removedMain = target.isMainPhoto();
        pharmacyPhotoRepository.delete(target);

        List<PharmacyPhoto> left = listByPharmacyId(pharmacyId);
        if (removedMain && !left.isEmpty()) {
            PharmacyPhoto first = left.get(0);
            first.setMainPhoto(true);
            pharmacyPhotoRepository.save(first);
        }

        syncLegacyMainPhoto(pharmacyId);
        return target;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PharmacyPhoto> getMainPhoto(Long pharmacyId) {
        return pharmacyPhotoRepository.findFirstByPharmacyIdAndMainPhotoTrueOrderBySortOrderAscIdAsc(pharmacyId)
                .or(() -> listByPharmacyId(pharmacyId).stream().findFirst());
    }

    @Override
    @Transactional
    public void ensureLegacyPhotoImported(Long pharmacyId) {
        List<PharmacyPhoto> current = listByPharmacyId(pharmacyId);
        if (!current.isEmpty()) {
            return;
        }

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (pharmacy.getPhotoFileId() == null || pharmacy.getPhotoFileId().isBlank()) {
            return;
        }

        pharmacyPhotoRepository.save(
                PharmacyPhoto.builder()
                        .pharmacyId(pharmacyId)
                        .fileId(pharmacy.getPhotoFileId())
                        .caption(null)
                        .sortOrder(1)
                        .mainPhoto(true)
                        .build()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getOrderedFileIds(Long pharmacyId) {
        return listByPharmacyId(pharmacyId).stream()
                .map(PharmacyPhoto::getFileId)
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    private void syncLegacyMainPhoto(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        String main = getMainPhoto(pharmacyId)
                .map(PharmacyPhoto::getFileId)
                .orElse(null);

        pharmacy.setPhotoFileId(main);
        pharmacyRepository.save(pharmacy);
    }
}
