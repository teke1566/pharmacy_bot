package com.tenahub.bot.service;

import com.tenahub.bot.dto.ReverseGeocodeResult;

public interface GeocodingService {
    ReverseGeocodeResult reverseGeocode(double latitude, double longitude);
}