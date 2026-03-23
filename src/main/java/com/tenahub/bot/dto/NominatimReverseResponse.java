package com.tenahub.bot.dto;

import java.util.Map;

import lombok.Data;

@Data
public class NominatimReverseResponse {
    private String display_name;
    private Map<String, Object> address;
}