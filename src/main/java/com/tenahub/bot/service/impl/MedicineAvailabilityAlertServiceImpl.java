package com.tenahub.bot.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tenahub.bot.entity.MedicineAvailabilityAlert;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.Pharmacy;import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.repository.MedicineAvailabilityAlertRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.UserFavoritePharmacyRepository;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.RatingService;
import com.tenahub.bot.util.GeoUtils;
import com.tenahub.bot.util.MedicineSearchNormalizer;
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

    @Value("${tenahub.alerts.default-radius-km:25}")
    private double defaultRadiusKm;

    @Value("${tenahub.alerts.default-cooldown-minutes:180}")
    private int defaultCooldownMinutes;

    @Value("${tenahub.alerts.default-max-notifications:5}")
    private int defaultMaxNotifications;

    @Value("${tenahub.alerts.default-expiry-days:30}")
    private int defaultExpiryDays;

    @Value("${tenahub.alerts.max-active-per-user:20}")
    private int maxActiveAlertsPerUser;

    @Override
    public void createAlert(Long userId, String medicineName, UserLocation location) {
        String normalized = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

        long activeCount = alertRepository.countByUserIdAndActiveTrue(userId);
        var existingActive = alertRepository
            .findByUserIdAndMedicineNameIgnoreCaseAndActiveTrue(userId, normalized)
            .orElse(null);

        if (existingActive == null && activeCount >= Math.max(1, maxActiveAlertsPerUser)) {
            throw new RuntimeException("You reached the active alert limit. Remove old alerts first.");
        }

        MedicineAvailabilityAlert alert = existingActive;
        if (alert == null) {
            alert = alertRepository
                .findTopByUserIdAndMedicineNameIgnoreCaseOrderByIdDesc(userId, normalized)
                .orElse(MedicineAvailabilityAlert.builder()
                    .userId(userId)
                    .medicineName(normalized)
                    .build());
        }

        alert.setUserId(userId);
        alert.setMedicineName(normalized);
        alert.setLatitude(location != null ? location.getLatitude() : null);
        alert.setLongitude(location != null ? location.getLongitude() : null);
        alert.setActive(true);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setNotifiedAt(null);
        alert.setNotificationsSent(0);
        alert.setLastNotifiedPharmacyId(null);
        alert.setRadiusKm(defaultRadiusKm <= 0 ? 25.0 : defaultRadiusKm);
        alert.setNotificationCooldownMinutes(Math.max(1, defaultCooldownMinutes));
        alert.setMaxNotifications(Math.max(1, defaultMaxNotifications));
        alert.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, defaultExpiryDays)));

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

    LocalDateTime now = LocalDateTime.now();

    for (MedicineAvailabilityAlert alert : alerts) {
        if (alert.getExpiresAt() != null && !alert.getExpiresAt().isAfter(now)) {
            alert.setActive(false);
            alertRepository.save(alert);
            continue;
        }

        Integer sentCount = alert.getNotificationsSent() == null ? 0 : alert.getNotificationsSent();
        Integer maxCount = alert.getMaxNotifications() == null ? 1 : Math.max(1, alert.getMaxNotifications());
        if (sentCount >= maxCount) {
            alert.setActive(false);
            alertRepository.save(alert);
            continue;
        }

        Integer cooldownMinutes = alert.getNotificationCooldownMinutes() == null
                ? Math.max(1, defaultCooldownMinutes)
                : Math.max(1, alert.getNotificationCooldownMinutes());

        if (alert.getNotifiedAt() != null) {
            long minutesSinceLast = Duration.between(alert.getNotifiedAt(), now).toMinutes();
            if (minutesSinceLast < cooldownMinutes) {
                continue;
            }
        }

        if (alert.getLastNotifiedPharmacyId() != null
                && alert.getLastNotifiedPharmacyId().equals(pharmacy.getId())
                && alert.getNotifiedAt() != null) {
            long minutesSinceLast = Duration.between(alert.getNotifiedAt(), now).toMinutes();
            if (minutesSinceLast < cooldownMinutes) {
                continue;
            }
        }

        double distance = -1;
        if (alert.getLatitude() != null
                && alert.getLongitude() != null
                && pharmacy.getLatitude() != null
                && pharmacy.getLongitude() != null) {
            distance = GeoUtils.distance(
                    alert.getLatitude(),
                    alert.getLongitude(),
                    pharmacy.getLatitude(),
                    pharmacy.getLongitude()
            );

            Double radius = alert.getRadiusKm() == null ? Math.max(1.0, defaultRadiusKm) : Math.max(1.0, alert.getRadiusKm());
            if (distance > radius) {
                continue;
            }
        }

        telegramClient.sendMessage(
                alert.getUserId(),
                "🔔 <b>Medicine Available</b>\n\n" +
                        "💊 " + medicineName + " is now back in stock.\n" +
                        (distance >= 0 ? "📏 Distance: " + String.format("%.2f", distance) + " km\n" : "") +
                        "🧠 Smart alert remains active until max notifications or expiry."
        );

        boolean canRate = !ratingService.hasUserRated(pharmacy.getId(), alert.getUserId());
        boolean isFavorite = favoritePharmacyRepository
                .findByUserIdAndPharmacyId(alert.getUserId(), pharmacy.getId())
                .isPresent();

        boolean temporaryClosureActive = isTemporaryClosureActive(pharmacy);

        boolean openNow = pharmacy.getOpenTime() != null
                && pharmacy.getCloseTime() != null
            && !temporaryClosureActive
                && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());

        PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), medicineName)
            .orElse(null);

        telegramClient.sendPharmacyResult(
                alert.getUserId(),
                pharmacy.getName(),
                pharmacy.getArea(),
                pharmacy.getPhone(),
                distance >= 0 ? distance : null,
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                pharmacy.getId(),
                pharmacy.getRating(),
                canRate,
                isFavorite,
                quantity,
                false,
                medicineName,
                inventory == null ? null : inventory.getId(),
                getPriceForMedicine(pharmacy.getId(), medicineName),
                openNow,
                pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
                pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
                temporaryClosureActive,
                temporaryClosureActive ? pharmacy.getTemporaryClosureReason() : null
        );

        alert.setNotifiedAt(now);
        alert.setLastNotifiedPharmacyId(pharmacy.getId());
        alert.setNotificationsSent(sentCount + 1);
        if (alert.getNotificationsSent() >= maxCount) {
            alert.setActive(false);
        }
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

private boolean isTemporaryClosureActive(Pharmacy pharmacy) {
    if (pharmacy == null || !pharmacy.isTemporarilyClosed()) {
        return false;
    }

    return pharmacy.getTemporaryClosedUntil() == null
            || pharmacy.getTemporaryClosedUntil().isAfter(LocalDateTime.now());
}

}