package com.tenahub.bot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReverseGeocodeResult {
    private String formattedAddress;
    private String region;
    private String city;
    private String subCity;
    private String area;
    private String landmark;
    private String plusCode;
}