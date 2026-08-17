package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.ReservationStatusHistoryDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.ReservationStatusHistory;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.ReservationStatusHistoryRepository;
import com.tenahub.bot.service.ReservationStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationStatusHistoryServiceImpl implements ReservationStatusHistoryService {

    private final ReservationStatusHistoryRepository historyRepository;
    private final PharmacyRepository pharmacyRepository;

    @Override
    @Transactional
    public void record(MedicineReservation reservation,
                       String fromStatus,
                       String toStatus,
                       Long actorTelegramId,
                       String reason) {
        if (reservation == null || reservation.getId() == null || reservation.getPharmacyId() == null) {
            return;
        }
        historyRepository.save(ReservationStatusHistory.builder()
                .reservationId(reservation.getId())
                .pharmacyId(reservation.getPharmacyId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorTelegramId(actorTelegramId)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public List<ReservationStatusHistoryDTO> listForPharmacy(Long pharmacyTelegramId, Long reservationId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        return historyRepository.findByReservationIdAndPharmacyIdOrderByCreatedAtAsc(reservationId, pharmacy.getId())
                .stream()
                .map(row -> ReservationStatusHistoryDTO.builder()
                        .historyId(row.getId())
                        .reservationId(row.getReservationId())
                        .fromStatus(row.getFromStatus())
                        .toStatus(row.getToStatus())
                        .actorTelegramId(row.getActorTelegramId())
                        .reason(row.getReason())
                        .createdAt(row.getCreatedAt())
                        .build())
                .toList();
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }
}
