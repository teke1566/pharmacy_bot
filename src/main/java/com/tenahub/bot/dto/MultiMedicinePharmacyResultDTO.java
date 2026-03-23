package com.tenahub.bot.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MultiMedicinePharmacyResultDTO {
    private Long pharmacyId;
    private String name;
    private String area;
    private String phone;
    private double latitude;
    private double longitude;
    private double distance;
    private double rating;
    private boolean openNow;
    private int matchedCount;
    private List<String> matchedMedicines = new ArrayList<>();
    private List<String> missingMedicines = new ArrayList<>();
}