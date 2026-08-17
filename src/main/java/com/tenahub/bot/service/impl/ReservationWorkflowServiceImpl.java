package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyNotificationType;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.ReservationWorkflowService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationWorkflowServiceImpl implements ReservationWorkflowService {

    private final PharmacyRepository pharmacyRepository;
    private final TelegramClient telegramClient;
    private final PharmacyNotificationService pharmacyNotificationService;

    @Override
    public void notifyPharmacyPendingReservation(MedicineReservation reservation, long pendingTimeoutMinutes) {
        runAfterCommit(() -> doNotify(reservation, pendingTimeoutMinutes));
    }

    @Override
    public void notifyPharmacyPendingReservations(List<MedicineReservation> reservations, long pendingTimeoutMinutes) {
        runAfterCommit(() -> doNotifyGroup(reservations, pendingTimeoutMinutes));
    }

    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun(task);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(task);
            }
        });
    }

    private void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Pharmacy reservation notification failed: {}", e.getMessage(), e);
        }
    }

    private void doNotify(MedicineReservation reservation, long pendingTimeoutMinutes) {
        if (reservation == null) {
            log.error("Cannot notify pharmacy: reservation is null");
            return;
        }

        // Prescription-required reservations get one combined pharmacy card after upload
        // (Prescription Review). Skip the early "waiting for upload" card to avoid duplicates.
        if (isAwaitingPrescriptionUpload(reservation)) {
            log.info("Skipping waiting-for-upload pharmacy card for reservation {} — review card sent after Rx upload",
                    reservation.getId());
            return;
        }

        Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
        if (pharmacy == null) {
            log.error("Cannot notify pharmacy: pharmacy {} not found for reservation {}",
                    reservation.getPharmacyId(), reservation.getId());
            return;
        }

        if (pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
            log.error("Pharmacy Telegram ID is missing for pharmacy {} (reservation {}). "
                            + "The pharmacy must /start the bot at least once.",
                    pharmacy.getId(), reservation.getId());
            return;
        }

        telegramClient.sendReservationRequestToPharmacy(
                pharmacy.getTelegramId(),
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMedicineName(),
                reservation.getRequestedQuantity(),
                reservation.getCustomerPhone(),
                reservation.getCustomerName(),
                pendingTimeoutMinutes,
                false
        );
        pharmacyNotificationService.create(
                pharmacy.getId(),
                PharmacyNotificationType.RESERVATION_PENDING,
                "New reservation",
                "Reservation #" + reservation.getId() + " for " + reservation.getMedicineName()
                        + " (qty " + reservation.getRequestedQuantity() + ")",
                reservation.getId(),
                reservation.getMedicineName());
    }

    private void doNotifyGroup(List<MedicineReservation> reservations, long pendingTimeoutMinutes) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }
        if (reservations.size() == 1) {
            doNotify(reservations.get(0), pendingTimeoutMinutes);
            return;
        }

        // If every item is still waiting for prescription upload, skip the early group card.
        boolean allAwaitingUpload = reservations.stream().allMatch(this::isAwaitingPrescriptionUpload);
        if (allAwaitingUpload) {
            log.info("Skipping waiting-for-upload grouped pharmacy card for group {} — review card sent after Rx upload",
                    reservations.get(0).getReservationGroupId());
            return;
        }

        MedicineReservation first = reservations.get(0);
        Pharmacy pharmacy = pharmacyRepository.findById(first.getPharmacyId()).orElse(null);
        if (pharmacy == null) {
            log.error("Cannot notify pharmacy: pharmacy {} not found for reservation group {}",
                    first.getPharmacyId(), first.getReservationGroupId());
            return;
        }
        if (pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
            log.error("Pharmacy Telegram ID is missing for pharmacy {} (group {}). "
                            + "The pharmacy must /start the bot at least once.",
                    pharmacy.getId(), first.getReservationGroupId());
            return;
        }

        String groupId = first.getReservationGroupId();
        if (groupId == null || groupId.isBlank()) {
            for (MedicineReservation reservation : reservations) {
                doNotify(reservation, pendingTimeoutMinutes);
            }
            return;
        }

        telegramClient.sendPharmacyGroupedReservationCard(pharmacy.getTelegramId(), groupId, reservations);
        String medicineSummary = reservations.stream()
                .map(MedicineReservation::getMedicineName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("medicines");
        pharmacyNotificationService.create(
                pharmacy.getId(),
                PharmacyNotificationType.RESERVATION_PENDING,
                "New grouped reservation",
                "Group " + groupId + " with " + reservations.size() + " items: " + medicineSummary,
                first.getId(),
                first.getMedicineName());
    }

    private boolean isAwaitingPrescriptionUpload(MedicineReservation reservation) {
        return reservation != null
                && reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED;
    }
}
