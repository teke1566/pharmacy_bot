package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyMiniAppProfileDTO {
    private Long id;
    private String name;
    private String city;
    private String area;
    private String phone;
    private double latitude;
    private double longitude;
    private String formattedAddress;
    private String landmark;
    private double rating;
    private boolean approved;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean temporarilyClosed;
    private String temporaryClosureReason;
    private LocalDateTime temporaryClosedUntil;
    private LocalDateTime lastInventoryUpdate;
    private String photoFileId;
    private boolean licenseSuspended;
    private LocalDate licenseExpiryDate;
    private String licenseUpdateStatus;
    private LocalDate pendingLicenseExpiryDate;
}
