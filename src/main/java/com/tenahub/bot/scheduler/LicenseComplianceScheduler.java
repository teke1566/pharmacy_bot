package com.tenahub.bot.scheduler;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LicenseComplianceScheduler {

    private static final int MAX_ALERT_WINDOW_DAYS = 30;
    private static final Set<Long> ALERT_MILESTONES_DAYS = Set.of(30L, 14L, 7L, 1L);

    private final PharmacyRepository pharmacyRepository;
    private final TelegramClient telegramClient;
    private final LocalizationService localizationService;

    @Scheduled(cron = "0 20 8 * * *")
    public void processLicenseCompliance() {
        LocalDate today = LocalDate.now();

        suspendMissingExpiryDates(today);
        sendNearExpiryAlerts(today);
        suspendExpiredLicenses(today);
    }

    private void suspendMissingExpiryDates(LocalDate today) {
        List<Pharmacy> missingExpiry = pharmacyRepository
                .findByApprovedTrueAndLicenseExpiryDateIsNullAndLicenseSuspendedFalse();

        for (Pharmacy pharmacy : missingExpiry) {
            if (hasActiveGrace(pharmacy, today)) {
                continue;
            }

            pharmacy.setLicenseSuspended(true);
            pharmacy.setLastExpiryAlertSentDate(today);
            pharmacyRepository.save(pharmacy);

            if (pharmacy.getTelegramId() == null) {
                continue;
            }

            try {
                telegramClient.sendMessage(
                        pharmacy.getTelegramId(),
                    localizationService.text(pharmacy.getTelegramId(), "license_missing_expiry_suspended"),
                        "HTML"
                );
            } catch (Exception ignored) {
                // Best-effort suspension notice for missing expiry-date records.
            }
        }
    }

    private void sendNearExpiryAlerts(LocalDate today) {
        LocalDate threshold = today.plusDays(MAX_ALERT_WINDOW_DAYS);

        List<Pharmacy> nearExpiry = pharmacyRepository
                .findByLicenseExpiryDateBetweenAndLicenseSuspendedFalse(today, threshold);

        for (Pharmacy pharmacy : nearExpiry) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, pharmacy.getLicenseExpiryDate());
            boolean skipAlert = pharmacy.getTelegramId() == null
                    || today.equals(pharmacy.getLastExpiryAlertSentDate())
                    || !ALERT_MILESTONES_DAYS.contains(daysLeft);

            if (skipAlert) {
                continue;
            }

            try {
                telegramClient.sendMessage(
                        pharmacy.getTelegramId(),
                    localizationService.text(pharmacy.getTelegramId(), "license_expiry_reminder", pharmacy.getLicenseExpiryDate(), daysLeft),
                        "HTML"
                );
            } catch (Exception ignored) {
                    // Best-effort reminder delivery: keep scheduler running even if one chat fails.
            }

            pharmacy.setLastExpiryAlertSentDate(today);
            pharmacyRepository.save(pharmacy);
        }
    }

    private void suspendExpiredLicenses(LocalDate today) {
        List<Pharmacy> expired = pharmacyRepository.findByLicenseExpiryDateBeforeAndLicenseSuspendedFalse(today);

        for (Pharmacy pharmacy : expired) {
            if (hasActiveGrace(pharmacy, today)) {
                continue;
            }

            pharmacy.setLicenseSuspended(true);
            pharmacyRepository.save(pharmacy);

            if (pharmacy.getTelegramId() == null) {
                continue;
            }

            try {
                telegramClient.sendMessage(
                        pharmacy.getTelegramId(),
                    localizationService.text(pharmacy.getTelegramId(), "license_expired_suspended", pharmacy.getLicenseExpiryDate()),
                        "HTML"
                );
            } catch (Exception ignored) {
                // Best-effort suspension notice: do not interrupt compliance processing.
            }
        }
    }

    private boolean hasActiveGrace(Pharmacy pharmacy, LocalDate today) {
        return pharmacy.getGracePeriodUntil() != null
                && !pharmacy.getGracePeriodUntil().isBefore(today);
    }
}
