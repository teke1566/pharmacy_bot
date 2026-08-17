package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineSuggestionResult;
import com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRatingRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.GeoUtils;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.MedicineSuggestionEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PharmacyServiceImpl implements PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyRatingRepository ratingRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private static final int SEARCH_RESULT_LIMIT = 10;
    private static final int NEARBY_RESULT_LIMIT = 12;

    @Override
    public List<PharmacyResponseDTO> searchMedicine(String medicine) {
        return searchMedicine(medicine, null);
    }

    @Override
    public List<PharmacyResponseDTO> searchMedicine(String medicine, Long catalogMedicineId) {
        List<PharmacyInventory> inventoryList = findInventoryForSearch(medicine, catalogMedicineId);

        return inventoryList.stream()
            .map(item -> pharmacyRepository.findById(item.getPharmacyId())
                .filter(p -> !p.isLicenseSuspended())
                .map(p -> mapBasic(p, item))
                .orElse(null))
            .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<PharmacyResponseDTO> searchMedicineWithArea(String medicine, String area) {
        String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine);
        if (normalizedMedicine.isBlank()) {
            return List.of();
        }

        List<PharmacyInventory> inventoryList =
                visibleForCustomerSearch(inventoryRepository.findByMedicineNameIgnoreCase(normalizedMedicine));

        return inventoryList.stream()
            .map(item -> pharmacyRepository.findById(item.getPharmacyId())
                .filter(p -> !p.isLicenseSuspended())
                .filter(p -> p.getArea() != null && p.getArea().toLowerCase().contains(area.toLowerCase()))
                .map(p -> mapBasic(p, item))
                .orElse(null))
            .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<PharmacyResponseDTO> searchMedicineWithCity(String medicine, String city) {
        String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine);
        if (normalizedMedicine.isBlank()) {
            return List.of();
        }

        List<PharmacyInventory> inventoryList =
                visibleForCustomerSearch(inventoryRepository.findByMedicineNameIgnoreCase(normalizedMedicine));

        return inventoryList.stream()
            .map(item -> pharmacyRepository.findById(item.getPharmacyId())
                .filter(p -> !p.isLicenseSuspended())
                .filter(p -> p.getCity() != null && p.getCity().toLowerCase().contains(city.toLowerCase()))
                .map(p -> mapBasic(p, item))
                .orElse(null))
            .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }
@Override
public List<String> suggestMedicines(String input) {
    return suggestMedicineOptions(input).typoSuggestions();
}

@Override
public MedicineSuggestionResult suggestMedicineOptions(String input) {
    if (input == null || input.isBlank()) {
        return new MedicineSuggestionResult("", List.of(), List.of());
    }

    return MedicineSuggestionEngine.build(input, inventoryRepository.findAllDistinctMedicineNames());
}

@Override
public List<PharmacyResponseDTO> searchMedicineNearby(
        String medicine,
        double userLat,
        double userLon,
        Long userId) {
    return searchMedicineNearby(medicine, null, userLat, userLon, userId);
}

@Override
public List<PharmacyResponseDTO> searchMedicineNearby(
        String medicine,
        Long catalogMedicineId,
        double userLat,
        double userLon,
        Long userId) {

    List<PharmacyInventory> inventoryList = findInventoryForSearch(medicine, catalogMedicineId);

    List<PharmacyResponseDTO> allResults = new ArrayList<>();

    for (PharmacyInventory item : inventoryList) {

        Optional<Pharmacy> pharmacyOpt = pharmacyRepository.findById(item.getPharmacyId());

        if (pharmacyOpt.isEmpty()) {
            continue;
        }

        Pharmacy p = pharmacyOpt.get();

        if (p.isLicenseSuspended()) {
            continue;
        }

        if (p.getLatitude() == null || p.getLongitude() == null) {
            continue;
        }

        double distance = GeoUtils.distance(
                userLat,
                userLon,
                p.getLatitude(),
                p.getLongitude()
        );

        boolean alreadyRated = ratingRepository.existsByPharmacyIdAndUserId(
                p.getId(),
                userId
        );

        boolean outOfStock =
                item.isOutOfStock()
                        || item.getQuantity() == null
                        || item.getQuantity() <= 0;

        boolean temporaryClosureActive = isTemporaryClosureActive(p);
        boolean openNow = !temporaryClosureActive && isOpenNow(p.getOpenTime(), p.getCloseTime());

        double distanceScore = 100 / (distance + 1);
        double ratingScore = p.getRating() * 10;
        double trustScore = p.isApproved() ? 50 : 0;
        double openScore = openNow ? 20 : 0;
        double stockScore = outOfStock ? -100 : Math.min(item.getQuantity(), 50);

        double totalScore =
                distanceScore +
                ratingScore +
                trustScore +
                openScore +
                stockScore;

        PharmacyResponseDTO dto = PharmacyResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .city(p.getCity())
                .area(p.getArea())
                .landmark(p.getLandmark())
                .formattedAddress(p.getFormattedAddress())
                .phone(p.getPhone())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .distance(distance)
                .rating(p.getRating())
                .approved(p.isApproved())
                .verified(isVerifiedPharmacy(p))
                .score(totalScore)
                .canRate(!alreadyRated)
                .stockQuantity(item.getQuantity())
                .outOfStock(outOfStock)
                .medicineName(item.getMedicineName())
                .medicineId(item.getId())
                .catalogMedicineId(item.getCatalogMedicineId())
                .price(item.getPrice())
                .requiresPrescription(item.isRequiresPrescription())
                .openNow(openNow)
                .openTime(p.getOpenTime() == null ? null : p.getOpenTime().toString())
                .closeTime(p.getCloseTime() == null ? null : p.getCloseTime().toString())
                .temporarilyClosed(temporaryClosureActive)
                .temporaryClosureReason(temporaryClosureActive ? p.getTemporaryClosureReason() : null)
                .temporaryClosedUntil(temporaryClosureActive ? p.getTemporaryClosedUntil() : null)
                .build();

        allResults.add(dto);
    }

    Comparator<PharmacyResponseDTO> sorter = (a, b) -> {
        if (a.isOutOfStock() != b.isOutOfStock()) {
            return a.isOutOfStock() ? 1 : -1;
        }
        return Double.compare(b.getScore(), a.getScore());
    };

    return allResults.stream()
            .sorted(sorter)
            .limit(SEARCH_RESULT_LIMIT)
            .collect(Collectors.toList());
}

@Override
public List<PharmacyResponseDTO> listNearbyApproved(Double userLat, Double userLon) {
    boolean hasUserCoords = userLat != null && userLon != null;
    List<PharmacyResponseDTO> results = new ArrayList<>();

    for (Pharmacy pharmacy : pharmacyRepository.findByApprovedTrue()) {
        if (pharmacy.isLicenseSuspended()) {
            continue;
        }
        if (pharmacy.getLatitude() == null || pharmacy.getLongitude() == null) {
            if (hasUserCoords) {
                continue;
            }
        }

        double distance = 0;
        if (hasUserCoords && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
            distance = GeoUtils.distance(userLat, userLon, pharmacy.getLatitude(), pharmacy.getLongitude());
        }

        boolean temporaryClosureActive = isTemporaryClosureActive(pharmacy);
        boolean openNow = !temporaryClosureActive && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());

        results.add(PharmacyResponseDTO.builder()
                .id(pharmacy.getId())
                .name(pharmacy.getName())
                .city(pharmacy.getCity())
                .area(pharmacy.getArea())
                .landmark(pharmacy.getLandmark())
                .formattedAddress(pharmacy.getFormattedAddress())
                .phone(pharmacy.getPhone())
                .latitude(pharmacy.getLatitude() == null ? 0 : pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude() == null ? 0 : pharmacy.getLongitude())
                .distance(distance)
                .rating(pharmacy.getRating())
                .approved(pharmacy.isApproved())
                .verified(isVerifiedPharmacy(pharmacy))
                .openNow(openNow)
                .openTime(pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString())
                .closeTime(pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString())
                .temporarilyClosed(temporaryClosureActive)
                .temporaryClosureReason(temporaryClosureActive ? pharmacy.getTemporaryClosureReason() : null)
                .temporaryClosedUntil(temporaryClosureActive ? pharmacy.getTemporaryClosedUntil() : null)
                .build());
    }

    Comparator<PharmacyResponseDTO> sorter = hasUserCoords
            ? Comparator.comparingDouble(PharmacyResponseDTO::getDistance)
            : Comparator.comparing(PharmacyResponseDTO::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    return results.stream()
            .sorted(sorter)
            .limit(NEARBY_RESULT_LIMIT)
            .collect(Collectors.toList());
}
   private List<PharmacyInventory> findInventoryForSearch(String medicine, Long catalogMedicineId) {
        List<PharmacyInventory> matches;
        if (catalogMedicineId != null) {
            List<PharmacyInventory> byCatalog = inventoryRepository.findByCatalogMedicineId(catalogMedicineId);
            matches = byCatalog.isEmpty() && medicine != null && !medicine.isBlank()
                    ? inventoryRepository.findByMedicineNameIgnoreCase(
                            MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine))
                    : byCatalog;
        } else if (medicine == null || medicine.isBlank()) {
            matches = List.of();
        } else {
            String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine);
            matches = normalizedMedicine.isBlank()
                    ? List.of()
                    : inventoryRepository.findByMedicineNameIgnoreCase(normalizedMedicine);
        }
        return visibleForCustomerSearch(matches);
   }

   private List<PharmacyInventory> visibleForCustomerSearch(List<PharmacyInventory> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().filter(item -> !item.isArchived()).toList();
   }

   private PharmacyResponseDTO mapBasic(Pharmacy p, PharmacyInventory item) {
    int qty = item.getQuantity() == null ? 0 : item.getQuantity();
    boolean expired = item.getExpiryDate() != null && item.getExpiryDate().isBefore(LocalDate.now());
    return PharmacyResponseDTO.builder()
            .id(p.getId())
            .name(p.getName())
            .city(p.getCity())
            .area(p.getArea())
            .landmark(p.getLandmark())
            .formattedAddress(p.getFormattedAddress())
            .phone(p.getPhone())
            .latitude(p.getLatitude())
            .longitude(p.getLongitude())
            .rating(p.getRating())
            .approved(p.isApproved())
            .verified(isVerifiedPharmacy(p))
        .stockQuantity(qty)
        .outOfStock(expired || item.isOutOfStock() || qty <= 0)
        .expired(expired)
        .medicineName(item.getMedicineName())
        .medicineId(item.getId())
        .catalogMedicineId(item.getCatalogMedicineId())
        .price(item.getPrice())
            .requiresPrescription(item.isRequiresPrescription())
            .openNow(!isTemporaryClosureActive(p) && isOpenNow(p.getOpenTime(), p.getCloseTime()))
            .openTime(p.getOpenTime() == null ? null : p.getOpenTime().toString())
            .closeTime(p.getCloseTime() == null ? null : p.getCloseTime().toString())
            .temporarilyClosed(isTemporaryClosureActive(p))
            .temporaryClosureReason(isTemporaryClosureActive(p) ? p.getTemporaryClosureReason() : null)
            .temporaryClosedUntil(isTemporaryClosureActive(p) ? p.getTemporaryClosedUntil() : null)
            .build();
}

private boolean isVerifiedPharmacy(Pharmacy pharmacy) {
    if (pharmacy == null) {
        return false;
    }

    boolean licenseApproved = pharmacy.isApproved() && !pharmacy.isLicenseSuspended();

    String normalizedPhone = pharmacy.getPhone() == null
            ? ""
            : pharmacy.getPhone().replaceAll("\\s+", "");
    boolean phoneConfirmed = normalizedPhone.matches("^\\+?[0-9]{7,15}$");

    boolean inventoryUpdatedRecently = pharmacy.getLastInventoryUpdate() != null
            && pharmacy.getLastInventoryUpdate().isAfter(LocalDateTime.now().minusHours(72));

    return licenseApproved && phoneConfirmed && inventoryUpdatedRecently;
}

  private boolean isOpenNow(LocalTime open, LocalTime close) {
    if (open == null || close == null) {
        return false;
    }

    LocalTime now = LocalTime.now();

    if (close.equals(open)) {
        return true; // assume 24 hours
    }

    if (close.isAfter(open)) {
        return (!now.isBefore(open) && !now.isAfter(close));
    }

    // overnight range, e.g. 22:00 -> 06:00
    return (!now.isBefore(open) || !now.isAfter(close));
}

private boolean isTemporaryClosureActive(Pharmacy pharmacy) {
    if (pharmacy == null || !pharmacy.isTemporarilyClosed()) {
        return false;
    }

    return pharmacy.getTemporaryClosedUntil() == null
            || pharmacy.getTemporaryClosedUntil().isAfter(LocalDateTime.now());
}

    @Override
    public boolean isRegisteredPharmacy(Long telegramId) {
        return pharmacyRepository.findByTelegramId(telegramId).isPresent();
    }

    @Override
    public void updatePhone(Long telegramId, String phone) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        pharmacy.setPhone(phone);
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void updateLocation(Long telegramId, Double latitude, Double longitude, String city, String area,
                               String formattedAddress, String landmark) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        if (latitude == null || longitude == null) {
            throw new RuntimeException("Latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new RuntimeException("Coordinates are out of range");
        }

        pharmacy.setLatitude(latitude);
        pharmacy.setLongitude(longitude);
        if (city != null && !city.isBlank()) {
            pharmacy.setCity(city.trim());
        }
        if (area != null && !area.isBlank()) {
            pharmacy.setArea(area.trim());
        }
        if (formattedAddress != null) {
            pharmacy.setFormattedAddress(formattedAddress.isBlank() ? null : formattedAddress.trim());
        }
        if (landmark != null) {
            pharmacy.setLandmark(landmark.isBlank() ? null : landmark.trim());
        }
        pharmacyRepository.save(pharmacy);
    }

 @Override
public void updateMedicines(Long telegramId, String medicines) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    String normalizedMedicines = MedicineSearchNormalizer.normalizeCommaSeparatedMedicines(medicines);

    pharmacy.setMedicines(normalizedMedicines);
    pharmacyRepository.save(pharmacy);

    List<String> medicineList = Arrays.stream(normalizedMedicines.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(m -> !m.isBlank())
            .distinct()
            .toList();

    for (String medicineName : medicineList) {
        boolean exists = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), medicineName)
                .isPresent();

        if (!exists) {
            PharmacyInventory newItem = PharmacyInventory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(medicineName)
                    .quantity(0)
                    .outOfStock(true)
                    .lowStockAlertSent(false)
                    .build();

            inventoryRepository.save(newItem);
        }
    }
}

    @Override
    public void updateHours(Long telegramId, String openTime, String closeTime) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        pharmacy.setOpenTime(LocalTime.parse(openTime));
        pharmacy.setCloseTime(LocalTime.parse(closeTime));
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void updateLicense(Long telegramId, String fileId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        pharmacy.setLicenseFileId(fileId);
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void setTemporaryClosure(Long telegramId, String reason, int durationHours) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        int safeDuration = Math.max(1, durationHours);
        pharmacy.setTemporarilyClosed(true);
        pharmacy.setTemporaryClosureReason(
                (reason == null || reason.isBlank()) ? "Temporarily closed by pharmacy" : reason.trim()
        );
        pharmacy.setTemporaryClosedUntil(LocalDateTime.now().plusHours(safeDuration));
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void clearTemporaryClosure(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        pharmacy.setTemporarilyClosed(false);
        pharmacy.setTemporaryClosureReason(null);
        pharmacy.setTemporaryClosedUntil(null);
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void savePendingLicenseUpdate(Long telegramId, String fileId, LocalDate expiryDate) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if ("PENDING".equalsIgnoreCase(pharmacy.getLicenseUpdateStatus())) {
            throw new RuntimeException("A license update is already pending admin approval");
        }

        pharmacy.setPendingLicenseFileId(fileId);
        pharmacy.setPendingLicenseExpiryDate(expiryDate);
        pharmacy.setLicenseUpdateStatus("PENDING");
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void approvePendingLicenseUpdate(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (pharmacy.getPendingLicenseFileId() == null || pharmacy.getPendingLicenseFileId().isBlank()) {
            throw new RuntimeException("No pending license update found");
        }

        pharmacy.setLicenseFileId(pharmacy.getPendingLicenseFileId());
        pharmacy.setLicenseExpiryDate(pharmacy.getPendingLicenseExpiryDate());
        pharmacy.setPendingLicenseFileId(null);
        pharmacy.setPendingLicenseExpiryDate(null);
        pharmacy.setLicenseUpdateStatus("APPROVED");
        pharmacy.setLicenseSuspended(false);
        pharmacy.setLastExpiryAlertSentDate(null);
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public void rejectPendingLicenseUpdate(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (pharmacy.getPendingLicenseFileId() == null || pharmacy.getPendingLicenseFileId().isBlank()) {
            throw new RuntimeException("No pending license update found");
        }

        pharmacy.setPendingLicenseFileId(null);
        pharmacy.setPendingLicenseExpiryDate(null);
        pharmacy.setLicenseUpdateStatus("REJECTED");
        pharmacyRepository.save(pharmacy);
    }
  @Override
public List<MultiMedicinePharmacyResultDTO> searchMultipleMedicinesNearby(
        List<String> medicines,
        double userLat,
        double userLon,
        Long userId
) {
    List<Pharmacy> pharmacies = pharmacyRepository.findByApprovedTrue();

    List<String> normalizedMedicines = medicines.stream()
            .map(MedicineSearchNormalizer::normalizeToEnglishCanonical)
            .filter(m -> !m.isBlank())
            .distinct()
            .toList();

    List<MultiMedicinePharmacyResultDTO> results = new ArrayList<>();

    for (Pharmacy pharmacy : pharmacies) {

        if (pharmacy.isLicenseSuspended()) {
            continue;
        }

        if (pharmacy.getLatitude() == null || pharmacy.getLongitude() == null) {
            continue;
        }

        List<PharmacyInventory> inventoryList = visibleForCustomerSearch(
                inventoryRepository.findByPharmacyId(pharmacy.getId()));

        Map<String, PharmacyInventory> inventoryMap = new HashMap<>();
        for (PharmacyInventory item : inventoryList) {
            if (item.getMedicineName() != null) {
                inventoryMap.put(item.getMedicineName().trim().toLowerCase(), item);
            }
        }

        MultiMedicinePharmacyResultDTO dto = new MultiMedicinePharmacyResultDTO();
        dto.setPharmacyId(pharmacy.getId());
        dto.setName(pharmacy.getName());
        dto.setCity(pharmacy.getCity());
        dto.setArea(pharmacy.getArea());
        dto.setLandmark(pharmacy.getLandmark());
        dto.setFormattedAddress(pharmacy.getFormattedAddress());
        dto.setPhone(pharmacy.getPhone());
        dto.setLatitude(pharmacy.getLatitude());
        dto.setLongitude(pharmacy.getLongitude());
        dto.setRating(pharmacy.getRating());

        double distance = GeoUtils.distance(
        userLat,
        userLon,
        pharmacy.getLatitude().doubleValue(),
        pharmacy.getLongitude().doubleValue()
);
        dto.setDistance(distance);

        dto.setOpenNow(!isTemporaryClosureActive(pharmacy) && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()));
        dto.setTemporarilyClosed(isTemporaryClosureActive(pharmacy));
        if (pharmacy.getOpenTime() != null) {
            dto.setOpenTime(pharmacy.getOpenTime().toString());
        }
        if (pharmacy.getCloseTime() != null) {
            dto.setCloseTime(pharmacy.getCloseTime().toString());
        }

        for (String medicine : normalizedMedicines) {
            PharmacyInventory item = inventoryMap.get(medicine);

            if (item != null && !item.isOutOfStock() && item.getQuantity() != null && item.getQuantity() > 0) {
                dto.getMatchedMedicines().add(medicine);
                dto.getMatchedMedicineIds().add(item.getId());
            } else {
                dto.getMissingMedicines().add(medicine);
            }
        }

        dto.setMatchedCount(dto.getMatchedMedicines().size());

        if (dto.getMatchedCount() > 0) {
            results.add(dto);
        }
    }

    results.sort(
            Comparator.comparingInt(MultiMedicinePharmacyResultDTO::getMatchedCount).reversed()
                    .thenComparing((MultiMedicinePharmacyResultDTO r) -> !r.isOpenNow())
                    .thenComparingDouble(MultiMedicinePharmacyResultDTO::getDistance)
                    .thenComparing(Comparator.comparingDouble(MultiMedicinePharmacyResultDTO::getRating).reversed())
    );

    return results;
}
@Override
public List<String> suggestAlternativeMedicines(String medicine) {
    return suggestMedicineOptions(medicine).alternativeSuggestions();
}
@Override
public boolean medicineExistsInCatalog(String medicine) {
    if (medicine == null || medicine.isBlank()) {
        return false;
    }

    String normalized = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine);

    return inventoryRepository.findAllDistinctMedicineNames().stream()
            .anyMatch(m -> m.equalsIgnoreCase(normalized));
}

}
