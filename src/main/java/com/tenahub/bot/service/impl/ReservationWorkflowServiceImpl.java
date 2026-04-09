package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationWorkflowService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationWorkflowServiceImpl implements ReservationWorkflowService {

    private final PharmacyRepository pharmacyRepository;
    private final TelegramClient telegramClient;

    @Override
    public void notifyPharmacyPendingReservation(MedicineReservation reservation, long pendingTimeoutMinutes) {
        if (reservation == null) {
            throw new RuntimeException("Reservation not found");
        }

        Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
            throw new RuntimeException("Pharmacy Telegram ID is missing");
        }

        telegramClient.sendReservationRequestToPharmacy(
                pharmacy.getTelegramId(),
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMedicineName(),
                reservation.getRequestedQuantity(),
                reservation.getCustomerPhone(),
                reservation.getCustomerName(),
                pendingTimeoutMinutes
        );
    }
}