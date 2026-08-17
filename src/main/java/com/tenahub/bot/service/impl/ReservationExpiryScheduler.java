package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Legacy APPROVED-only expiry scheduler. Disabled by default because
 * {@link com.tenahub.bot.scheduler.ReservationScheduler} already expires
 * APPROVED + READY_FOR_PICKUP. Enable only with
 * tenahub.reservation.legacy-expiry-scheduler-enabled=true.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tenahub.reservation.legacy-expiry-scheduler-enabled", havingValue = "true")
public class ReservationExpiryScheduler {

    private final MedicineReservationRepository reservationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final ReservationService reservationService;
    private final TelegramClient telegramClient;

    @Scheduled(fixedRate = 60000)
    public void expireApprovedReservations() {
        List<MedicineReservation> expired =
                reservationRepository.findByStatusAndExpiresAtBefore(
                        MedicineReservationStatus.APPROVED,
                        LocalDateTime.now()
                );

        for (MedicineReservation reservation : expired) {
            reservationService.expireReservation(reservation.getId());

            telegramClient.sendMessage(
                    reservation.getUserId(),
                    "⌛ Your reservation has expired.\n\n" +
                    "💊 Medicine: " + reservation.getMedicineName() + "\n" +
                    "🔢 Quantity: " + reservation.getRequestedQuantity() + "\n\n" +
                    "You can search again and place a new reservation."
            );

            Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
            if (pharmacy != null) {
                telegramClient.sendMessage(
                        pharmacy.getTelegramId(),
                        "⌛ A reservation expired.\n\n" +
                        "🆔 ID: " + reservation.getId() + "\n" +
                        "💊 Medicine: " + reservation.getMedicineName() + "\n" +
                        "🔢 Quantity: " + reservation.getRequestedQuantity() + "\n\n" +
                        "This stock is available again."
                );
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime upcoming = now.plusMinutes(15);

        List<MedicineReservation> reservations =
                reservationRepository.findByStatusAndExpiresAtBetweenAndReminderSentFalse(
                        MedicineReservationStatus.APPROVED,
                        now,
                        upcoming
                );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");

        for (MedicineReservation reservation : reservations) {
            reservation.setReminderSent(true);
            reservationRepository.save(reservation);

            telegramClient.sendMessage(
                    reservation.getUserId(),
                    "⏰ Reminder: your reservation expires soon.\n\n" +
                    "💊 Medicine: " + reservation.getMedicineName() + "\n" +
                    "🔢 Quantity: " + reservation.getRequestedQuantity() + "\n" +
                    "⏳ Expires around: " + reservation.getExpiresAt().format(formatter)
            );
        }
    }
}
