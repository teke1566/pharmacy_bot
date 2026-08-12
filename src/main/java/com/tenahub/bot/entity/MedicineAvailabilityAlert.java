package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "medicine_availability_alerts")
public class MedicineAvailabilityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String medicineName;

    private Double latitude;

    private Double longitude;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "radius_km", nullable = false)
    private Double radiusKm = 25.0;

    @Builder.Default
    @Column(name = "notification_cooldown_minutes", nullable = false)
    private Integer notificationCooldownMinutes = 180;

    @Builder.Default
    @Column(name = "max_notifications", nullable = false)
    private Integer maxNotifications = 5;

    @Builder.Default
    @Column(name = "notifications_sent", nullable = false)
    private Integer notificationsSent = 0;

    private LocalDateTime createdAt;

    private LocalDateTime notifiedAt;

    private LocalDateTime expiresAt;

    private Long lastNotifiedPharmacyId;
}