package com.tenahub.bot.handler;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminService;
import com.tenahub.bot.service.FavoritePharmacyService;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.MedicineSearchLogService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.service.RatingService;
import com.tenahub.bot.service.RegistrationService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.UserLocationService;
import com.tenahub.bot.util.TelegramClient;
import org.springframework.stereotype.Service;

@Service
public class TelegramMessageHandler extends TelegramHandlerSupport {

    public TelegramMessageHandler(
            PharmacyService pharmacyService,
            RegistrationService registrationService,
            TelegramClient telegramClient,
            UserLocationService userLocationService,
            RatingService ratingService,
            PharmacyRepository pharmacyRepository,
            InventoryService inventoryService,
            ReservationService reservationService,
            AdminService adminService,
            PharmacyInventoryRepository inventoryRepository,
            FavoritePharmacyService favoritePharmacyService,
            MedicineSearchLogService medicineSearchLogService,
            MedicineAvailabilityAlertService medicineAvailabilityAlertService
    ) {
        super(
                pharmacyService,
                registrationService,
                telegramClient,
                userLocationService,
                ratingService,
                pharmacyRepository,
                inventoryService,
                reservationService,
                adminService,
                inventoryRepository,
                favoritePharmacyService,
                medicineSearchLogService,
                medicineAvailabilityAlertService
        );
    }

    public void handleMessage(TelegramUpdateDTO update) {
        if (update.getMessage() == null) {
            return;
        }

        Long chatId = update.getMessage().getChat().getId();

        if (update.getMessage().getContact() != null) {
            handleContactMessage(update, chatId);
            return;
        }

        if (update.getMessage().getLocation() != null) {
            handleLocationMessage(update, chatId);
            return;
        }

        if (update.getMessage().getPhoto() != null) {
            handlePhotoMessage(update, chatId);
            return;
        }

        handleTextMessage(update, chatId);
    }

}
