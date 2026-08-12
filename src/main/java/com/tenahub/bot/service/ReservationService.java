package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservation;

import java.util.List;
import java.util.Map;

public interface ReservationService {

    MedicineReservation createReservation(
            Long userId,
            Long pharmacyId,
            String medicineName,
            Integer quantity,
            String customerPhone,
            String customerName
    );

    List<MedicineReservation> createReservationGroup(
            Long userId,
            Long pharmacyId,
            Map<String, Integer> medicineQuantities,
            String customerPhone,
            String customerName
    );

    MedicineReservation approveReservation(Long reservationId);

    MedicineReservation approveReservationAndNotify(Long reservationId);

    MedicineReservation rejectReservation(Long reservationId, String reason);

    MedicineReservation fulfillReservation(Long reservationId);

    MedicineReservation fulfillReservationAndNotify(Long reservationId);

    MedicineReservation scanReservationByQrToken(String qrToken, Long pharmacyTelegramId);

    MedicineReservation fulfillReservationForPharmacy(Long reservationId, Long pharmacyTelegramId);

    MedicineReservation fulfillReservationAndNotify(Long reservationId, Long pharmacyTelegramId);

    MedicineReservation expireReservation(Long reservationId);

    MedicineReservation cancelReservationByUser(Long userId, Long reservationId);

    MedicineReservation cancelReservationByPharmacy(Long reservationId, Long pharmacyTelegramId);

    MedicineReservation autoCancelPendingReservation(Long reservationId, String reason);

    String viewActiveReservations(Long chatId);

    String viewPendingReservations(Long pharmacyTelegramId);

    String viewFulfillableReservations(Long pharmacyTelegramId);

    String viewApprovedReservations(Long pharmacyTelegramId);

    String viewReservationHistory(Long userId);

    List<MedicineReservation> getUserReservations(Long userId);

    List<MedicineReservation> getPharmacyReservations(Long pharmacyTelegramId);

    void assertPharmacyOwnsReservation(Long reservationId, Long pharmacyTelegramId);

    String buildUserReservationStatusLabel(MedicineReservation reservation);
    List<MedicineReservation> getPendingReservations(Long pharmacyTelegramId);
    List<MedicineReservation> getPrescriptionReservations(Long pharmacyTelegramId);
    List<MedicineReservation> getApprovedReservations(Long pharmacyTelegramId);
    List<MedicineReservation> getFulfillableReservations(Long pharmacyTelegramId);
    void holdInventoryForApprovedPrescription(MedicineReservation reservation);
}