package com.tenahub.bot.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tenahub.bot.entity.MedicineAvailabilityAlert;
import com.tenahub.bot.entity.Pharmacy;import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.repository.MedicineAvailabilityAlertRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.UserFavoritePharmacyRepository;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.RatingService;
import com.tenahub.bot.util.GeoUtils;
import com.tenahub.bot.util.TelegramClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicineAvailabilityAlertServiceImpl implements MedicineAvailabilityAlertService {

    private final MedicineAvailabilityAlertRepository alertRepository;
    private final TelegramClient telegramClient;
    private final PharmacyInventoryRepository inventoryRepository;
private final RatingService ratingService;
private final UserFavoritePharmacyRepository favoritePharmacyRepository;

    @Override
    public void createAlert(Long userId, String medicineName, UserLocation location) {
        String normalized = medicineName.trim().toLowerCase();

        boolean exists = alertRepository
                .findByUserIdAndMedicineNameIgnoreCaseAndActiveTrue(userId, normalized)
                .isPresent();

        if (exists) {
            throw new RuntimeException("You already have an active alert for " + normalized);
        }

        MedicineAvailabilityAlert alert = MedicineAvailabilityAlert.builder()
                .userId(userId)
                .medicineName(normalized)
                .latitude(location != null ? location.getLatitude() : null)
                .longitude(location != null ? location.getLongitude() : null)
                .createdAt(LocalDateTime.now())
                .build();

        alertRepository.save(alert);
    }

    @Override
    public List<MedicineAvailabilityAlert> getActiveAlerts(Long userId) {
        return alertRepository.findByUserIdAndActiveTrue(userId);
    }

    @Override
    public void removeAlert(Long userId, Long alertId) {
        MedicineAvailabilityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));

        if (!alert.getUserId().equals(userId)) {
            throw new RuntimeException("You cannot remove this alert");
        }

        alert.setActive(false);
        alertRepository.save(alert);
    }

    @Override
    public void removeAllAlerts(Long userId) {
        List<MedicineAvailabilityAlert> alerts = alertRepository.findByUserIdAndActiveTrue(userId);

        for (MedicineAvailabilityAlert alert : alerts) {
            alert.setActive(false);
        }

        alertRepository.saveAll(alerts);
    }

    @Override
public void notifyUsersIfAvailable(String medicineName, Pharmacy pharmacy, Integer quantity) {
    List<MedicineAvailabilityAlert> alerts =
            alertRepository.findByActiveTrueAndMedicineNameIgnoreCase(medicineName);

    for (MedicineAvailabilityAlert alert : alerts) {
        if (alert.getLatitude() != null
                && alert.getLongitude() != null
                && pharmacy.getLatitude() != null
                && pharmacy.getLongitude() != null) {

            double distance = GeoUtils.distance(
                    alert.getLatitude(),
                    alert.getLongitude(),
                    pharmacy.getLatitude(),
                    pharmacy.getLongitude()
            );

            if (distance > 25) {
                continue;
            }

            telegramClient.sendMessage(
                    alert.getUserId(),
                    "🔔 <b>Medicine Available</b>\n\n" +
                    "💊 " + medicineName + " is now back in stock nearby."
            );

            boolean canRate = !ratingService.hasUserRated(pharmacy.getId(), alert.getUserId());
            boolean isFavorite = favoritePharmacyRepository
                    .findByUserIdAndPharmacyId(alert.getUserId(), pharmacy.getId())
                    .isPresent();

            boolean openNow = pharmacy.getOpenTime() != null
                    && pharmacy.getCloseTime() != null
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());

            telegramClient.sendPharmacyResult(
                    alert.getUserId(),
                    pharmacy.getName(),
                    pharmacy.getArea(),
                    pharmacy.getPhone(),
                    distance,
                    pharmacy.getLatitude(),
                    pharmacy.getLongitude(),
                    pharmacy.getId(),
                    pharmacy.getRating(),
                    canRate,
                    isFavorite,
                    quantity,
                    false,
                    medicineName,
                    getPriceForMedicine(pharmacy.getId(), medicineName),
                    openNow,
                    pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
                    pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString()
            );
        }

        alert.setActive(false);
        alert.setNotifiedAt(LocalDateTime.now());
        alertRepository.save(alert);
    }
}

private BigDecimal getPriceForMedicine(Long pharmacyId, String medicineName) {
    return inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .map(item -> item.getPrice())
            .orElse(null);
}
private boolean isOpenNow(java.time.LocalTime open, java.time.LocalTime close) {
    if (open == null || close == null) {
        return false;
    }

    java.time.LocalTime now = java.time.LocalTime.now();

    if (close.equals(open)) {
        return true;
    }

    if (close.isAfter(open)) {
        return !now.isBefore(open) && !now.isAfter(close);
    }

    return !now.isBefore(open) || !now.isAfter(close);
}
}