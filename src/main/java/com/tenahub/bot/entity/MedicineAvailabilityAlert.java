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

    private LocalDateTime createdAt;

    private LocalDateTime notifiedAt;
}