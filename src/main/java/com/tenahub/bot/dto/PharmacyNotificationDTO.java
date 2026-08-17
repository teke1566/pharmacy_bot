package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyNotificationDTO {
    private Long notificationId;
    private String type;
    private String title;
    private String message;
    private Long reservationId;
    private String medicineName;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
