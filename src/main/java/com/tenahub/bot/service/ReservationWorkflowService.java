package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservation;

import java.util.List;

public interface ReservationWorkflowService {

    void notifyPharmacyPendingReservation(MedicineReservation reservation, long pendingTimeoutMinutes);

    void notifyPharmacyPendingReservations(List<MedicineReservation> reservations, long pendingTimeoutMinutes);
}