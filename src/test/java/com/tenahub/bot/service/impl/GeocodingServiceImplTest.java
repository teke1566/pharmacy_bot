package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.NominatimReverseResponse;
import com.tenahub.bot.dto.ReverseGeocodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GeocodingServiceImpl service;

    @Test
    void reverseGeocode_mapsNominatimFieldsAndPlusCode() {
        NominatimReverseResponse body = new NominatimReverseResponse();
        body.setDisplay_name("Bole, Addis Ababa");
        body.setAddress(Map.of(
                "state", "Addis Ababa",
                "city", "Addis Ababa",
                "suburb", "Bole",
                "road", "Africa Avenue",
                "amenity", "Mall"
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(NominatimReverseResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        ReverseGeocodeResult result = service.reverseGeocode(9.01, 38.75);

        assertEquals("Bole, Addis Ababa", result.getFormattedAddress());
        assertEquals("Addis Ababa", result.getRegion());
        assertEquals("Addis Ababa", result.getCity());
        assertEquals("Bole", result.getSubCity());
        assertEquals("Africa Avenue", result.getArea());
        assertEquals("Mall", result.getLandmark());
        assertNotNull(result.getPlusCode());
    }

    @Test
    void reverseGeocode_fallsBackToPlusCodeWhenNominatimFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(NominatimReverseResponse.class)))
                .thenThrow(new RuntimeException("timeout"));

        ReverseGeocodeResult result = service.reverseGeocode(9.01, 38.75);

        assertNull(result.getFormattedAddress());
        assertNotNull(result.getPlusCode());
    }
}
