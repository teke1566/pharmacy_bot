package com.tenahub.bot.service;

import com.tenahub.bot.dto.ReservationStatusHistoryDTO;
import com.tenahub.bot.entity.MedicineReservation;

import java.util.List;

public interface ReservationStatusHistoryService {

    void record(MedicineReservation reservation,
                String fromStatus,
                String toStatus,
                Long actorTelegramId,
                String reason);

    List<ReservationStatusHistoryDTO> listForPharmacy(Long pharmacyTelegramId, Long reservationId);
}
