package com.tenahub.bot.scheduler;

import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationScheduler {

    private final MedicineReservationRepository reservationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final ReservationService reservationService;
    private final PrescriptionReviewService prescriptionReviewService;
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

    @Value("${tenahub.reservation.archive-idle-after-minutes:60}")
    private long archiveIdleAfterMinutes;

    @Scheduled(cron = "0 */5 * * * *")
    public void expireReservations() {

        List<MedicineReservation> expired =
                reservationRepository.findByStatusInAndExpiresAtBefore(
                        List.of(
                                MedicineReservationStatus.APPROVED,
                                MedicineReservationStatus.READY_FOR_PICKUP
                        ),
                        LocalDateTime.now()
                );

        for (MedicineReservation reservation : expired) {
            try {
                reservationService.expireReservation(reservation.getId());

                String statusUrl = telegramClient.buildMiniAppUserReservationStatusUrl(
                        "history",
                        reservation.getId(),
                        reservation.getReservationGroupId());
                telegramClient.sendMessageWithMiniAppButton(
                        reservation.getUserId(),
                        "⏳ Your reservation expired.\n\n"
                                + "💊 Medicine: " + reservation.getMedicineName() + "\n"
                                + "🔢 Quantity: " + reservation.getRequestedQuantity(),
                        statusUrl,
                        "📄 View reservation"
                );

            } catch (Exception ignored) {
            }
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void sendHoldExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime upcoming = now.plusMinutes(15);

        List<MedicineReservation> reservations =
                reservationRepository.findByStatusInAndExpiresAtBetweenAndReminderSentFalse(
                        List.of(
                                MedicineReservationStatus.APPROVED,
                                MedicineReservationStatus.READY_FOR_PICKUP
                        ),
                        now,
                        upcoming
                );

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a");
        for (MedicineReservation reservation : reservations) {
            try {
                reservation.setReminderSent(true);
                reservationRepository.save(reservation);
                if (reservation.getUserId() == null || reservation.getExpiresAt() == null) {
                    continue;
                }
                telegramClient.sendMessage(
                        reservation.getUserId(),
                        "⏰ Reminder: your reservation expires soon.\n\n"
                                + "💊 Medicine: " + reservation.getMedicineName() + "\n"
                                + "🔢 Quantity: " + reservation.getRequestedQuantity() + "\n"
                                + "⏳ Expires around: " + reservation.getExpiresAt().format(formatter)
                );
            } catch (Exception e) {
                log.warn("Failed to send hold expiry reminder for reservation {}: {}",
                        reservation.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void archiveIdleTerminalReservations() {
        if (archiveIdleAfterMinutes <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(archiveIdleAfterMinutes);
        List<MedicineReservation> candidates = reservationRepository.findTerminalNeedingArchive(
                List.of(
                        MedicineReservationStatus.FULFILLED,
                        MedicineReservationStatus.EXPIRED,
                        MedicineReservationStatus.REJECTED,
                        MedicineReservationStatus.CANCELLED
                ),
                cutoff
        );
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime hiddenAt = LocalDateTime.now();
        int updated = 0;
        for (MedicineReservation reservation : candidates) {
            boolean changed = false;
            if (reservation.getHiddenFromUserAt() == null) {
                reservation.setHiddenFromUserAt(hiddenAt);
                changed = true;
            }
            if (reservation.getHiddenFromPharmacyAt() == null) {
                reservation.setHiddenFromPharmacyAt(hiddenAt);
                changed = true;
            }
            if (changed) {
                updated++;
            }
        }
        if (updated > 0) {
            reservationRepository.saveAll(candidates);
            log.info("Auto soft-archived {} idle terminal reservation(s) older than {} minutes",
                    updated, archiveIdleAfterMinutes);
        }
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void autoCancelUnapprovedPendingReservations() {
        LocalDateTime now = LocalDateTime.now();
        sendPendingSlaReminders(now);

        long finalThresholdMinutes = getFinalThresholdMinutes();
        List<MedicineReservation> timedOutPending = reservationRepository.findByStatusOrderByCreatedAtDesc(
            MedicineReservationStatus.PENDING
        );

        for (MedicineReservation reservation : timedOutPending) {
            try {
                if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
                    continue;
                }
                if (isAwaitingPrescriptionUpload(reservation)) {
                    continue;
                }

                if (isAwaitingPrescriptionReview(reservation)) {
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

                telegramClient.sendMessageWithMiniAppButton(
                        cancelled.getUserId(),
                        "⌛ Your reservation request expired because the pharmacy did not respond in time.\n\n"
                                + "Please try another pharmacy or submit again.",
                        telegramClient.buildMiniAppUserReservationStatusUrl(
                                "history",
                                cancelled.getId(),
                                cancelled.getReservationGroupId()),
                        "📄 View reservation"
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

        List<MedicineReservation> firstReminderCandidates = reservationRepository.findByStatusOrderByCreatedAtDesc(
            MedicineReservationStatus.PENDING
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

            if (isAwaitingPrescriptionReview(reservation)) {
                sendPrescriptionReviewReminder(reservation, pharmacy.getTelegramId());
            } else {
                telegramClient.sendPendingReservationReminder(pharmacy.getTelegramId(), reservation, waitingMinutes);
            }
            reservation.setFirstReminderSentAt(now);
            reservationRepository.save(reservation);
        }

        List<MedicineReservation> secondReminderCandidates = reservationRepository.findByStatusOrderByCreatedAtDesc(
            MedicineReservationStatus.PENDING
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

            if (isAwaitingPrescriptionReview(reservation)) {
                sendPrescriptionReviewReminder(reservation, pharmacy.getTelegramId());
            } else {
                telegramClient.sendPendingReservationEscalation(pharmacy.getTelegramId(), reservation, waitingMinutes);
            }
            if (reservation.getFirstReminderSentAt() == null) {
                reservation.setFirstReminderSentAt(now);
            }
            reservation.setSecondReminderSentAt(now);
            reservationRepository.save(reservation);
        }
    }

    private void sendPrescriptionReviewReminder(MedicineReservation reservation, Long pharmacyTelegramId) {
        try {
            PrescriptionStatusResponseDTO status = prescriptionReviewService.getPrescriptionStatus(
                    reservation.getId(),
                    reservation.getReservationGroupId(),
                    null
            );
            telegramClient.sendPharmacyPrescriptionReviewCard(pharmacyTelegramId, status);
        } catch (Exception e) {
            log.warn("Failed to resend prescription review reminder for reservation {}: {}",
                    reservation.getId(), e.getMessage());
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
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED;
    }

    private boolean isAwaitingPrescriptionReview(MedicineReservation reservation) {
        return reservation != null
                && reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PENDING_REVIEW;
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