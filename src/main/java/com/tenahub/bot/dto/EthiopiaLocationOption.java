package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EthiopiaLocationOption {
    private String region;
    private String city;
    private String area;
    private double latitude;
    private double longitude;
}