package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicinePhoto;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicinePhotoRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.service.MedicinePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicinePhotoServiceImpl implements MedicinePhotoService {

    private final MedicinePhotoRepository medicinePhotoRepository;
    private final PharmacyInventoryRepository pharmacyInventoryRepository;
    private final RestTemplate restTemplate;

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.api-url}")
    private String baseApiUrl;

    @Override
    @Transactional(readOnly = true)
    public List<MedicinePhoto> listByMedicineId(Long medicineId) {
        return medicinePhotoRepository.findByMedicineIdOrderByMainPhotoDescSortOrderAscIdAsc(medicineId);
    }

    @Override
    @Transactional
    public MedicinePhoto addPhoto(Long medicineId, String telegramFileId, String caption) {
        if (medicineId == null) {
            throw new RuntimeException("Medicine id is required");
        }
        if (telegramFileId == null || telegramFileId.isBlank()) {
            throw new RuntimeException("Medicine photo file id is required");
        }

        ensureMedicineExists(medicineId);

        List<MedicinePhoto> current = listByMedicineId(medicineId);
        if (current.size() >= MAX_PHOTOS) {
            throw new RuntimeException("Maximum 4 medicine photos allowed");
        }

        int nextSort = current.stream()
                .map(MedicinePhoto::getSortOrder)
                .filter(v -> v != null)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        return medicinePhotoRepository.save(
                MedicinePhoto.builder()
                        .medicineId(medicineId)
                        .telegramFileId(telegramFileId)
                        .caption(caption)
                        .sortOrder(nextSort)
                        .mainPhoto(current.isEmpty())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
    }

    @Override
    @Transactional
    public MedicinePhoto setMainPhoto(Long medicineId, Long photoId) {
        ensureMedicineExists(medicineId);

        List<MedicinePhoto> current = listByMedicineId(medicineId);
        if (current.isEmpty()) {
            throw new RuntimeException("No medicine photos found");
        }

        MedicinePhoto target = current.stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Medicine photo not found"));

        for (MedicinePhoto photo : current) {
            photo.setMainPhoto(photo.getId().equals(photoId));
            photo.setUpdatedAt(LocalDateTime.now());
            medicinePhotoRepository.save(photo);
        }

        return target;
    }

    @Override
    @Transactional
    public MedicinePhoto removePhoto(Long medicineId, Long photoId) {
        ensureMedicineExists(medicineId);

        List<MedicinePhoto> current = listByMedicineId(medicineId);
        MedicinePhoto target = current.stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Medicine photo not found"));

        boolean removedMain = target.isMainPhoto();
        medicinePhotoRepository.delete(target);

        List<MedicinePhoto> left = listByMedicineId(medicineId);
        if (removedMain && !left.isEmpty()) {
            MedicinePhoto first = left.get(0);
            first.setMainPhoto(true);
            first.setUpdatedAt(LocalDateTime.now());
            medicinePhotoRepository.save(first);
        }

        return target;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MedicinePhoto> getMainPhoto(Long medicineId) {
        return medicinePhotoRepository.findFirstByMedicineIdAndMainPhotoTrueOrderBySortOrderAscIdAsc(medicineId)
                .or(() -> listByMedicineId(medicineId).stream().findFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getOrderedFileIds(Long medicineId) {
        return listByMedicineId(medicineId).stream()
                .map(MedicinePhoto::getTelegramFileId)
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getImageBytesByTelegramFileId(String telegramFileId) {
        if (telegramFileId == null || telegramFileId.isBlank()) {
            throw new RuntimeException("Medicine photo file id is required");
        }

        try {
            String apiUrl = baseApiUrl + "/bot" + botToken;

            String getFileUrl = apiUrl + "/getFile?file_id=" + telegramFileId;
            @SuppressWarnings("unchecked")
            Map<String, Object> getFileResponse = restTemplate.getForObject(getFileUrl, Map.class);

            if (getFileResponse == null || !getFileResponse.containsKey("result")) {
                throw new RuntimeException("Failed to get medicine photo file info from Telegram");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) getFileResponse.get("result");
            String filePath = (String) result.get("file_path");

            if (filePath == null || filePath.isBlank()) {
                throw new RuntimeException("No file path returned by Telegram");
            }

            String downloadUrl = baseApiUrl + "/file/bot" + botToken + "/" + filePath;
            byte[] fileData = restTemplate.getForObject(downloadUrl, byte[].class);

            if (fileData == null || fileData.length == 0) {
                throw new RuntimeException("Failed to download medicine photo data from Telegram");
            }

            return fileData;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error downloading medicine photo: " + e.getMessage(), e);
        }
    }

    private PharmacyInventory ensureMedicineExists(Long medicineId) {
        return pharmacyInventoryRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));
    }
}