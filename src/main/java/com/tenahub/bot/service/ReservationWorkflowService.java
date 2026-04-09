package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservation;

public interface ReservationWorkflowService {

    void notifyPharmacyPendingReservation(MedicineReservation reservation, long pendingTimeoutMinutes);
}