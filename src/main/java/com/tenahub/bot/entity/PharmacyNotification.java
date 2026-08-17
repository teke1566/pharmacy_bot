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
@Table(name = "pharmacy_notifications")
public class PharmacyNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private PharmacyNotificationType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "medicine_name", length = 255)
    private String medicineName;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
