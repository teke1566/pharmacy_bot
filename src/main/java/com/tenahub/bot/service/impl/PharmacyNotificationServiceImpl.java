package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyNotificationDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyNotification;
import com.tenahub.bot.entity.PharmacyNotificationType;
import com.tenahub.bot.repository.PharmacyNotificationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PharmacyNotificationServiceImpl implements PharmacyNotificationService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyNotificationRepository notificationRepository;

    @Override
    public List<PharmacyNotificationDTO> list(Long pharmacyTelegramId, boolean unreadOnly) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        List<PharmacyNotification> rows = unreadOnly
                ? notificationRepository.findByPharmacyIdAndReadAtIsNullOrderByCreatedAtDesc(pharmacy.getId())
                : notificationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public long unreadCount(Long pharmacyTelegramId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        return notificationRepository.countByPharmacyIdAndReadAtIsNull(pharmacy.getId());
    }

    @Override
    @Transactional
    public PharmacyNotificationDTO markRead(Long pharmacyTelegramId, Long notificationId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyNotification notification = notificationRepository.findByIdAndPharmacyId(notificationId, pharmacy.getId())
                .orElseThrow(() -> new RuntimeException("Notification does not belong to this pharmacy"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    @Override
    @Transactional
    public int markAllRead(Long pharmacyTelegramId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        List<PharmacyNotification> unread = notificationRepository
                .findByPharmacyIdAndReadAtIsNullOrderByCreatedAtDesc(pharmacy.getId());
        LocalDateTime now = LocalDateTime.now();
        for (PharmacyNotification notification : unread) {
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Override
    @Transactional
    public void create(Long pharmacyId,
                       PharmacyNotificationType type,
                       String title,
                       String message,
                       Long reservationId,
                       String medicineName) {
        if (pharmacyId == null || type == null) {
            return;
        }
        String safeTitle = title == null || title.isBlank() ? type.name() : title.trim();
        String safeMessage = message == null || message.isBlank() ? safeTitle : message.trim();
        notificationRepository.save(PharmacyNotification.builder()
                .pharmacyId(pharmacyId)
                .type(type)
                .title(safeTitle)
                .message(safeMessage)
                .reservationId(reservationId)
                .medicineName(medicineName)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private PharmacyNotificationDTO toDto(PharmacyNotification notification) {
        return PharmacyNotificationDTO.builder()
                .notificationId(notification.getId())
                .type(notification.getType() == null ? null : notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .reservationId(notification.getReservationId())
                .medicineName(notification.getMedicineName())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }
}
