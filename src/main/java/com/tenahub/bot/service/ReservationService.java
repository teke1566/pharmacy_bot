package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservation;

import java.util.List;

public interface ReservationService {

    MedicineReservation createReservation(
            Long userId,
            Long pharmacyId,
            String medicineName,
            Integer quantity,
            String customerPhone,
            String customerName
    );

    MedicineReservation approveReservation(Long reservationId);

    MedicineReservation rejectReservation(Long reservationId, String reason);

    MedicineReservation fulfillReservation(Long reservationId);

    MedicineReservation expireReservation(Long reservationId);

    MedicineReservation cancelReservationByUser(Long userId, Long reservationId);

    String viewActiveReservations(Long chatId);

    String viewPendingReservations(Long pharmacyTelegramId);

    String viewFulfillableReservations(Long pharmacyTelegramId);

    String viewApprovedReservations(Long pharmacyTelegramId);

    String viewReservationHistory(Long userId);

    List<MedicineReservation> getUserReservations(Long userId);

    String buildUserReservationStatusLabel(MedicineReservation reservation);
    List<MedicineReservation> getPendingReservations(Long pharmacyTelegramId);
List<MedicineReservation> getApprovedReservations(Long pharmacyTelegramId);
List<MedicineReservation> getFulfillableReservations(Long pharmacyTelegramId);
}