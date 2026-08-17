package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyMiniAppRegistrationStatusDTO;
import org.springframework.web.multipart.MultipartFile;

public interface PharmacyRegistrationMiniAppService {

    PharmacyMiniAppRegistrationStatusDTO getStatus(Long telegramId);

    PharmacyMiniAppRegistrationStatusDTO submit(
            Long telegramId,
            String name,
            String city,
            String area,
            String phone,
            String medicines,
            String openTime,
            String closeTime,
            Double latitude,
            Double longitude,
            String formattedAddress,
            String landmark,
            String plusCode,
            String licenseExpiryDate,
            MultipartFile licenseFile);

    PharmacyMiniAppRegistrationStatusDTO restart(Long telegramId);
}
