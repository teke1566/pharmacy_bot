package com.tenahub.bot.service;

import com.tenahub.bot.entity.UserLocation;

public interface UserLocationService {

    void saveLocation(Long telegramId, double lat, double lon);

    void saveLocation(Long telegramId,
                      double lat,
                      double lon,
                      String region,
                      String city,
                      String subCity,
                      String area,
                      String displayName);

    UserLocation getLocation(Long telegramId);
}