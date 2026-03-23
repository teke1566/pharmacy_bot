package com.tenahub.bot.service.impl;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.openlocationcode.OpenLocationCode;
import com.tenahub.bot.dto.NominatimReverseResponse;
import com.tenahub.bot.dto.ReverseGeocodeResult;
import com.tenahub.bot.service.GeocodingService;

@Service
public class GeocodingServiceImpl implements GeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ReverseGeocodeResult reverseGeocode(double latitude, double longitude) {
        try {
            String url = "https://nominatim.openstreetmap.org/reverse"
                    + "?format=jsonv2"
                    + "&lat=" + latitude
                    + "&lon=" + longitude
                    + "&addressdetails=1";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "tenahub-bot/1.0 (contact: admin@tenahub.local)");
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<NominatimReverseResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    NominatimReverseResponse.class
            );

            NominatimReverseResponse body = response.getBody();

            String formattedAddress = null;
            String region = null;
            String city = null;
            String subCity = null;
            String area = null;
            String landmark = null;

            if (body != null) {
                formattedAddress = body.getDisplay_name();

                Map<String, Object> address = body.getAddress();
                region = get(address, "state");

                city = firstNonBlank(
                        get(address, "city"),
                        get(address, "town"),
                        get(address, "county")
                );

                subCity = firstNonBlank(
                        get(address, "suburb"),
                        get(address, "borough"),
                        get(address, "city_district")
                );

                area = firstNonBlank(
                        get(address, "neighbourhood"),
                        get(address, "quarter"),
                        get(address, "road"),
                        get(address, "residential")
                );

                landmark = firstNonBlank(
                        get(address, "amenity"),
                        get(address, "building"),
                        get(address, "tourism"),
                        get(address, "shop")
                );
            }

            // Real Plus Code from lat/lng
            String plusCode = OpenLocationCode.encode(latitude, longitude);

            return ReverseGeocodeResult.builder()
                    .formattedAddress(formattedAddress)
                    .region(region)
                    .city(city)
                    .subCity(subCity)
                    .area(area)
                    .landmark(landmark)
                    .plusCode(plusCode)
                    .build();

        } catch (Exception e) {
            System.out.println("reverseGeocode error: " + e.getMessage());

            // even if geocoder fails, still return plus code
            try {
                String plusCode = OpenLocationCode.encode(latitude, longitude);

                return ReverseGeocodeResult.builder()
                        .formattedAddress(null)
                        .region(null)
                        .city(null)
                        .subCity(null)
                        .area(null)
                        .landmark(null)
                        .plusCode(plusCode)
                        .build();
            } catch (Exception ex) {
                System.out.println("plusCode encode error: " + ex.getMessage());
            }

            return ReverseGeocodeResult.builder()
                    .formattedAddress(null)
                    .region(null)
                    .city(null)
                    .subCity(null)
                    .area(null)
                    .landmark(null)
                    .plusCode(null)
                    .build();
        }
    }

    private String get(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return null;
        }
        return String.valueOf(map.get(key));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}