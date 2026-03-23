package com.tenahub.bot.service;


import com.tenahub.bot.entity.PharmacyRegistration;

public interface RegistrationService {

    Long register(String name,
                  String city,
                  String area,
                  String phone,
                  String medicines,
                  String openTime,
                  String closeTime,
                  Long telegramId);

    void saveLocation(Long telegramId, Double latitude, Double longitude);

    Long saveLicense(Long telegramId, String fileId);

    Long approve(Long registrationId);

    Long reject(Long registrationId);

    void rejectWithReason(Long registrationId, String reason);

    boolean exists(Long telegramId);

    boolean licenseAlreadyUploaded(Long telegramId);

    boolean isProcessed(Long id);

    PharmacyRegistration getRegistration(Long id);

    Long getApprovedPharmacyId(Long telegramId);

    boolean isRegisteredPharmacy(Long telegramId);

    boolean existsById(Long id);

    void deleteRegistration(Long id);

    void deletePendingByTelegramId(Long telegramId);

    void deleteRegistrationByTelegramId(Long telegramId);

    int deleteInvalidPendingRegistrations();

    PharmacyRegistration getLatestRejected(Long telegramId);

    Long restartRejectedRegistration(Long telegramId);

}