package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.repository.UserLocationRepository;
import com.tenahub.bot.service.UserLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserLocationServiceImpl implements UserLocationService {

    private final UserLocationRepository repository;

    @Override
    public void saveLocation(Long telegramId, double lat, double lon) {
        UserLocation location = repository.findByTelegramId(telegramId)
                .orElse(UserLocation.builder()
                        .telegramId(telegramId)
                        .build());

        location.setLatitude(lat);
        location.setLongitude(lon);

        // keep old readable values if they already exist
        if (location.getDisplayName() == null || location.getDisplayName().isBlank()) {
            location.setDisplayName("Exact location saved");
        }

        repository.save(location);
    }

    @Override
    public void saveLocation(Long telegramId,
                             double lat,
                             double lon,
                             String region,
                             String city,
                             String subCity,
                             String area,
                             String displayName) {

        UserLocation location = repository.findByTelegramId(telegramId)
                .orElse(UserLocation.builder()
                        .telegramId(telegramId)
                        .build());

        location.setLatitude(lat);
        location.setLongitude(lon);
        location.setRegion(region);
        location.setCity(city);
        location.setSubCity(subCity);
        location.setArea(area);
        location.setDisplayName(displayName);

        repository.save(location);
    }

    @Override
    public UserLocation getLocation(Long telegramId) {
        return repository.findByTelegramId(telegramId).orElse(null);
    }
}