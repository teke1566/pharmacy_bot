package com.tenahub.bot.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MultiMedicinePharmacyResultDTO {
    private Long pharmacyId;
    private String name;
    private String city;
    private String area;
    private String landmark;
    private String formattedAddress;
    private String phone;
    private double latitude;
    private double longitude;
    private double distance;
    private double rating;
    private boolean openNow;
    private String openTime;
    private String closeTime;
    private boolean temporarilyClosed;
    private boolean favourite;
    private int matchedCount;
    private List<String> matchedMedicines = new ArrayList<>();
    private List<Long> matchedMedicineIds = new ArrayList<>();
    private List<String> missingMedicines = new ArrayList<>();
}