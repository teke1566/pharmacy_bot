package com.tenahub.bot.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pharmacies")
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String city;

    private String area;

    private String phone;

    private String medicines;

    private Double latitude;

    private Double longitude;
    private String formattedAddress;
    private String landmark;
    private String plusCode;

    // user rating average
    private double rating;

    // admin approval
    private boolean approved;

    // inventory freshness
    private LocalDateTime lastInventoryUpdate;

    // open hours
    private LocalTime openTime;
    private LocalTime closeTime;

    @Column(length = 2000)
    private String inventory;
    // format example: insulin:10,paracetamol:50

    @Column(name = "telegram_id")
    private Long telegramId;

    private String licenseFileId;

    // NEW FIELDS FOR LICENSE UPDATE APPROVAL FLOW
    private String pendingLicenseFileId;

    private String licenseUpdateStatus; // PENDING / APPROVED / REJECTED
    @Column(name = "photo_file_id")
    private String photoFileId;
}