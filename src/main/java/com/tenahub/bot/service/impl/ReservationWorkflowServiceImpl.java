package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.PharmacyRepository;
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
                isAwaitingPrescriptionUpload(reservation)
        );
    }

    private void doNotifyGroup(List<MedicineReservation> reservations, long pendingTimeoutMinutes) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }
        if (reservations.size() == 1) {
            doNotify(reservations.get(0), pendingTimeoutMinutes);
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
    }

    private boolean isAwaitingPrescriptionUpload(MedicineReservation reservation) {
        return reservation != null
                && reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED;
    }
}
