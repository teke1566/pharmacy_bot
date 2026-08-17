package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyMiniAppRegistrationStatusDTO {
    private String status;
    private Long registrationId;
    private String rejectionReason;
    private String name;
    private String city;
    private String area;
    private String phone;
    private String medicines;
    private String openTime;
    private String closeTime;
    private Double latitude;
    private Double longitude;
    private String formattedAddress;
    private String landmark;
    private String plusCode;
    private String licenseExpiryDate;
}
