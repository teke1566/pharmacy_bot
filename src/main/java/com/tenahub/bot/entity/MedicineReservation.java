package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private Long userId;

    private String medicineName;

    private Integer requestedQuantity;

    @Enumerated(EnumType.STRING)
    private MedicineReservationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime fulfilledAt;

    @Builder.Default
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    private String rejectionReason;

    private String customerPhone;

    private String customerName;

    private String note;
}