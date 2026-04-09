package com.tenahub.bot.scheduler;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final MedicineReservationRepository reservationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final ReservationService reservationService;
    private final TelegramClient telegramClient;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private long pendingTimeoutMinutes;

    @Value("${tenahub.reservation.sla.first-reminder-minutes:10}")
    private long firstReminderMinutes;

    @Value("${tenahub.reservation.sla.second-reminder-minutes:18}")
    private long secondReminderMinutes;

    @Value("${tenahub.reservation.sla.final-action-minutes:0}")
    private long finalActionMinutes;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    @Scheduled(cron = "0 */5 * * * *")
    public void expireReservations() {

        List<MedicineReservation> expired =
                reservationRepository.findByStatusAndExpiresAtBefore(
                        MedicineReservationStatus.APPROVED,
                        LocalDateTime.now()
                );

        for (MedicineReservation reservation : expired) {
            try {
                reservationService.expireReservation(reservation.getId());

                telegramClient.sendMessage(
                        reservation.getUserId(),
                        "⏳ Your reservation expired.\n\n"
                                + "💊 Medicine: " + reservation.getMedicineName() + "\n"
                                + "🔢 Quantity: " + reservation.getRequestedQuantity()
                );

            } catch (Exception ignored) {
            }
        }
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void autoCancelUnapprovedPendingReservations() {
        LocalDateTime now = LocalDateTime.now();
        sendPendingSlaReminders(now);

        long finalThresholdMinutes = getFinalThresholdMinutes();
        LocalDateTime cutoff = now.minusMinutes(finalThresholdMinutes);

        List<MedicineReservation> timedOutPending = reservationRepository.findByStatusAndCreatedAtBefore(
                MedicineReservationStatus.PENDING,
                cutoff
        );

        for (MedicineReservation reservation : timedOutPending) {
            try {
                if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
                    continue;
                }
                if (isAwaitingPrescriptionUpload(reservation)) {
                    continue;
                }

                long waitingMinutes = waitingMinutes(reservation, now);
                if (waitingMinutes < finalThresholdMinutes) {
                    continue;
                }
                Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
                Long pharmacyTelegramId = pharmacy != null ? pharmacy.getTelegramId() : null;

                if (reservation.getSlaEscalatedAt() == null) {
                    reservation.setSlaEscalatedAt(now);
                    reservationRepository.save(reservation);
                    telegramClient.sendPendingReservationEscalationToAdmin(
                            adminChatId,
                            reservation,
                            pharmacyTelegramId,
                            waitingMinutes
                    );
                }

                MedicineReservation cancelled = reservationService.autoCancelPendingReservation(
                        reservation.getId(),
                        "AUTO_CANCELLED_PENDING_TIMEOUT"
                );

                if (cancelled.getStatus() != MedicineReservationStatus.CANCELLED) {
                    continue;
                }

                telegramClient.sendMessage(
                        cancelled.getUserId(),
                        "⌛ Your reservation request expired because the pharmacy did not respond in time.\n\n"
                                + "Please try another pharmacy or submit again."
                );

                if (pharmacy != null && pharmacy.getTelegramId() != null && pharmacy.getTelegramId() > 0) {
                    telegramClient.sendMessage(
                            pharmacy.getTelegramId(),
                            "⌛ Pending reservation auto-cancelled due to SLA timeout.\n\n"
                                    + "🆔 ID: " + cancelled.getId() + "\n"
                                    + "💊 Medicine: " + cancelled.getMedicineName() + "\n"
                                    + "🔢 Quantity: " + cancelled.getRequestedQuantity() + "\n"
                                    + "👤 User ID: " + cancelled.getUserId() + "\n"
                                    + "⏱ Waiting: " + waitingMinutes + " minutes"
                    );
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void sendPendingSlaReminders(LocalDateTime now) {
        long firstThresholdMinutes = getFirstThresholdMinutes();
        long secondThresholdMinutes = getSecondThresholdMinutes();
        long finalThresholdMinutes = getFinalThresholdMinutes();

        LocalDateTime firstCutoff = now.minusMinutes(firstThresholdMinutes);
        List<MedicineReservation> firstReminderCandidates = reservationRepository.findByStatusAndCreatedAtBefore(
                MedicineReservationStatus.PENDING,
                firstCutoff
        );

        for (MedicineReservation reservation : firstReminderCandidates) {
            if (reservation.getFirstReminderSentAt() != null || reservation.getStatus() != MedicineReservationStatus.PENDING) {
                continue;
            }
            if (isAwaitingPrescriptionUpload(reservation)) {
                continue;
            }

            long waitingMinutes = waitingMinutes(reservation, now);
            if (waitingMinutes < firstThresholdMinutes || waitingMinutes >= secondThresholdMinutes) {
                continue;
            }

            Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
            if (pharmacy == null || pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
                continue;
            }

            telegramClient.sendPendingReservationReminder(pharmacy.getTelegramId(), reservation, waitingMinutes);
            reservation.setFirstReminderSentAt(now);
            reservationRepository.save(reservation);
        }

        LocalDateTime secondCutoff = now.minusMinutes(secondThresholdMinutes);
        List<MedicineReservation> secondReminderCandidates = reservationRepository.findByStatusAndCreatedAtBefore(
                MedicineReservationStatus.PENDING,
                secondCutoff
        );

        for (MedicineReservation reservation : secondReminderCandidates) {
            if (reservation.getSecondReminderSentAt() != null || reservation.getStatus() != MedicineReservationStatus.PENDING) {
                continue;
            }
            if (isAwaitingPrescriptionUpload(reservation)) {
                continue;
            }

            long waitingMinutes = waitingMinutes(reservation, now);
            if (waitingMinutes < secondThresholdMinutes || waitingMinutes >= finalThresholdMinutes) {
                continue;
            }

            Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
            if (pharmacy == null || pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
                continue;
            }

            telegramClient.sendPendingReservationEscalation(pharmacy.getTelegramId(), reservation, waitingMinutes);
            if (reservation.getFirstReminderSentAt() == null) {
                reservation.setFirstReminderSentAt(now);
            }
            reservation.setSecondReminderSentAt(now);
            reservationRepository.save(reservation);
        }
    }

    private long waitingMinutes(MedicineReservation reservation, LocalDateTime now) {
        if (reservation == null) {
            return 0;
        }

        LocalDateTime waitingStartedAt = resolvePendingStartTime(reservation);
        if (waitingStartedAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(waitingStartedAt, now).toMinutes());
    }

    private LocalDateTime resolvePendingStartTime(MedicineReservation reservation) {
        if (reservation == null) {
            return null;
        }
        if (reservation.getPendingExpiresAt() != null) {
            return reservation.getPendingExpiresAt().minusMinutes(pendingTimeoutMinutes);
        }
        return reservation.getCreatedAt();
    }

    private boolean isAwaitingPrescriptionUpload(MedicineReservation reservation) {
        return reservation != null
                && reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED;
    }

    private long getFirstThresholdMinutes() {
        return Math.max(1, firstReminderMinutes);
    }

    private long getSecondThresholdMinutes() {
        return Math.max(getFirstThresholdMinutes() + 1, secondReminderMinutes);
    }

    private long getFinalThresholdMinutes() {
        long configuredFinal = finalActionMinutes > 0 ? finalActionMinutes : pendingTimeoutMinutes;
        return Math.max(getSecondThresholdMinutes() + 1, configuredFinal);
    }
}