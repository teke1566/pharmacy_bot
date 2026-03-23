package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRatingRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Override
    public List<PharmacyResponseDTO> searchMedicine(String medicine) {
        List<PharmacyInventory> inventoryList =
                inventoryRepository.findByMedicineNameIgnoreCase(medicine);

        return inventoryList.stream()
                .map(item -> pharmacyRepository.findById(item.getPharmacyId()).orElse(null))
                .filter(p -> p != null)
                .map(this::mapBasic)
                .collect(Collectors.toList());
    }

    @Override
    public List<PharmacyResponseDTO> searchMedicineWithArea(String medicine, String area) {
        List<PharmacyInventory> inventoryList =
                inventoryRepository.findByMedicineNameIgnoreCase(medicine);

        return inventoryList.stream()
                .map(item -> pharmacyRepository.findById(item.getPharmacyId()).orElse(null))
                .filter(p -> p != null)
                .filter(p -> p.getArea() != null && p.getArea().toLowerCase().contains(area.toLowerCase()))
                .map(this::mapBasic)
                .collect(Collectors.toList());
    }

    @Override
    public List<PharmacyResponseDTO> searchMedicineWithCity(String medicine, String city) {
        List<PharmacyInventory> inventoryList =
                inventoryRepository.findByMedicineNameIgnoreCase(medicine);

        return inventoryList.stream()
                .map(item -> pharmacyRepository.findById(item.getPharmacyId()).orElse(null))
                .filter(p -> p != null)
                .filter(p -> p.getCity() != null && p.getCity().toLowerCase().contains(city.toLowerCase()))
                .map(this::mapBasic)
                .collect(Collectors.toList());
    }
@Override
public List<String> suggestMedicines(String input) {
    if (input == null || input.isBlank()) {
        return List.of();
    }

    String normalizedInput = input.trim().toLowerCase();

    List<String> medicines = inventoryRepository.findAllDistinctMedicineNames();

    return medicines.stream()
            .filter(m -> m != null && !m.isBlank())
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(m -> m.startsWith(normalizedInput))
            .distinct()
            .sorted()
            .limit(SEARCH_RESULT_LIMIT)
            .toList();
}

@Override
public List<PharmacyResponseDTO> searchMedicineNearby(
        String medicine,
        double userLat,
        double userLon,
        Long userId) {

    if (medicine == null || medicine.isBlank()) {
        return List.of();
    }

    String normalizedMedicine = medicine.trim().toLowerCase();

    List<PharmacyInventory> inventoryList =
            inventoryRepository.findByMedicineNameIgnoreCase(normalizedMedicine);

    List<PharmacyResponseDTO> allResults = new ArrayList<>();

    for (PharmacyInventory item : inventoryList) {

        Optional<Pharmacy> pharmacyOpt = pharmacyRepository.findById(item.getPharmacyId());

        if (pharmacyOpt.isEmpty()) {
            continue;
        }

        Pharmacy p = pharmacyOpt.get();

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

        boolean openNow = isOpenNow(p.getOpenTime(), p.getCloseTime());

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
                .area(p.getArea())
                .phone(p.getPhone())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .distance(distance)
                .rating(p.getRating())
                .approved(p.isApproved())
                .score(totalScore)
                .canRate(!alreadyRated)
                .stockQuantity(item.getQuantity())
                .outOfStock(outOfStock)
                .medicineName(item.getMedicineName())
                .price(item.getPrice())
                .openNow(openNow)
                .openTime(p.getOpenTime() == null ? null : p.getOpenTime().toString())
                .closeTime(p.getCloseTime() == null ? null : p.getCloseTime().toString())
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
   private PharmacyResponseDTO mapBasic(Pharmacy p) {
    return PharmacyResponseDTO.builder()
            .id(p.getId())
            .name(p.getName())
            .area(p.getArea())
            .phone(p.getPhone())
            .latitude(p.getLatitude())
            .longitude(p.getLongitude())
            .rating(p.getRating())
            .approved(p.isApproved())
            .openNow(isOpenNow(p.getOpenTime(), p.getCloseTime()))
            .openTime(p.getOpenTime() == null ? null : p.getOpenTime().toString())
            .closeTime(p.getCloseTime() == null ? null : p.getCloseTime().toString())
            .build();
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
public void updateMedicines(Long telegramId, String medicines) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    pharmacy.setMedicines(medicines);
    pharmacyRepository.save(pharmacy);

    List<String> medicineList = Arrays.stream(medicines.split(","))
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
    public void savePendingLicenseUpdate(Long telegramId, String fileId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if ("PENDING".equalsIgnoreCase(pharmacy.getLicenseUpdateStatus())) {
            throw new RuntimeException("A license update is already pending admin approval");
        }

        pharmacy.setPendingLicenseFileId(fileId);
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
        pharmacy.setPendingLicenseFileId(null);
        pharmacy.setLicenseUpdateStatus("APPROVED");
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
            .map(m -> m == null ? "" : m.trim().toLowerCase())
            .filter(m -> !m.isBlank())
            .distinct()
            .toList();

    List<MultiMedicinePharmacyResultDTO> results = new ArrayList<>();

    for (Pharmacy pharmacy : pharmacies) {

        if (pharmacy.getLatitude() == null || pharmacy.getLongitude() == null) {
            continue;
        }

        List<PharmacyInventory> inventoryList = inventoryRepository.findByPharmacyId(pharmacy.getId());

        Map<String, PharmacyInventory> inventoryMap = new HashMap<>();
        for (PharmacyInventory item : inventoryList) {
            if (item.getMedicineName() != null) {
                inventoryMap.put(item.getMedicineName().trim().toLowerCase(), item);
            }
        }

        MultiMedicinePharmacyResultDTO dto = new MultiMedicinePharmacyResultDTO();
        dto.setPharmacyId(pharmacy.getId());
        dto.setName(pharmacy.getName());
        dto.setArea(pharmacy.getArea());
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

        dto.setOpenNow(isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()));

        for (String medicine : normalizedMedicines) {
            PharmacyInventory item = inventoryMap.get(medicine);

            if (item != null && !item.isOutOfStock() && item.getQuantity() != null && item.getQuantity() > 0) {
                dto.getMatchedMedicines().add(medicine);
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
    if (medicine == null || medicine.isBlank()) {
        return List.of();
    }

    String normalizedInput = medicine.trim().toLowerCase();

    List<String> medicines = inventoryRepository.findAllDistinctMedicineNames();

    return medicines.stream()
            .filter(m -> m != null && !m.isBlank())
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(m -> {
                return m.startsWith(normalizedInput)
                        || m.contains(normalizedInput)
                        || levenshteinDistance(m, normalizedInput) <= 2;
            })
            .distinct()
            .sorted()
            .limit(5)
            .toList();
}
@Override
public boolean medicineExistsInCatalog(String medicine) {
    if (medicine == null || medicine.isBlank()) {
        return false;
    }

    String normalized = medicine.trim().toLowerCase();

    return inventoryRepository.findAllDistinctMedicineNames().stream()
            .anyMatch(m -> m.equalsIgnoreCase(normalized));
}
private int levenshteinDistance(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];

    for (int i = 0; i <= a.length(); i++) {
        dp[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
        dp[0][j] = j;
    }

    for (int i = 1; i <= a.length(); i++) {
        for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;

            dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
            );
        }
    }

    return dp[a.length()][b.length()];
}

}
