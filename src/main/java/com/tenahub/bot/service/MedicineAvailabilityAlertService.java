package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineAvailabilityAlert;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.UserLocation;

import java.util.List;

public interface MedicineAvailabilityAlertService {
    void createAlert(Long userId, String medicineName, UserLocation location);
    List<MedicineAvailabilityAlert> getActiveAlerts(Long userId);
    void removeAlert(Long userId, Long alertId);
    void removeAllAlerts(Long userId);
    void notifyUsersIfAvailable(String medicineName, Pharmacy pharmacy, Integer quantity);
}