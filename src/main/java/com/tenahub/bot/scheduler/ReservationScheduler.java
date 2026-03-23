package com.tenahub.bot.scheduler;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final MedicineReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final TelegramClient telegramClient;

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
}