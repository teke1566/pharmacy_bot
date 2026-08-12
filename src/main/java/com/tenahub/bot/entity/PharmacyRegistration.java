package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pharmacy_registrations")
public class PharmacyRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String city;
    private String area;
    private String phone;
    private String medicines;
    private Long telegramId;
    private Double latitude;
    private Double longitude;
    private String licenseFileId;
    private String status;
    private String openTime;
    private String closeTime;

    private String formattedAddress;
    private String landmark;
    private String plusCode;
    private LocalDate licenseExpiryDate;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}