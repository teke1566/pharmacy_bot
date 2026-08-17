package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyNotificationDTO;
import com.tenahub.bot.entity.PharmacyNotificationType;

import java.util.List;

public interface PharmacyNotificationService {

    List<PharmacyNotificationDTO> list(Long pharmacyTelegramId, boolean unreadOnly);

    long unreadCount(Long pharmacyTelegramId);

    PharmacyNotificationDTO markRead(Long pharmacyTelegramId, Long notificationId);

    int markAllRead(Long pharmacyTelegramId);

    void create(Long pharmacyId,
                PharmacyNotificationType type,
                String title,
                String message,
                Long reservationId,
                String medicineName);
}
