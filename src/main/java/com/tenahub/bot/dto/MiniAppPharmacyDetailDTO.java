package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppPharmacyDetailDTO {
    private Long pharmacyId;
    private String name;
    private String city;
    private String area;
    private String phone;
    private Double latitude;
    private Double longitude;
    private String formattedAddress;
    private String landmark;
    private String plusCode;
    private double rating;
    private boolean approved;
    private boolean openNow;
    private String openTime;
    private String closeTime;
    private boolean temporarilyClosed;
    private String temporaryClosureReason;
    private List<MiniAppInventoryItemDTO> medicines;
}
