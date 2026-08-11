package com.tenahub.bot.handler;

import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.registration.AdminRejectSession;
import com.tenahub.bot.registration.AdminRejectSessionManager;
import com.tenahub.bot.registration.AdminRejectType;
import com.tenahub.bot.registration.AdminViewSession;
import com.tenahub.bot.registration.AdminViewSessionManager;
import com.tenahub.bot.registration.LocationSelectionSessionManager;
import com.tenahub.bot.registration.MedicineSearchSessionManager;
import com.tenahub.bot.registration.UpdateSession;
import com.tenahub.bot.registration.MedicineSelectionSession;
import com.tenahub.bot.registration.MedicineSelectionSessionManager;
import com.tenahub.bot.registration.MultiMedicineSearchSession;
import com.tenahub.bot.registration.MultiMedicineSearchSessionManager;
import com.tenahub.bot.registration.PharmacyDetailViewSession;
import com.tenahub.bot.registration.PharmacyDetailViewSessionManager;
import com.tenahub.bot.registration.RegistrationSession;
import com.tenahub.bot.registration.RegistrationSessionManager;
import com.tenahub.bot.registration.RegistrationStep;
import com.tenahub.bot.registration.ReservationSessionManager;
import com.tenahub.bot.registration.SearchFilterType;
import com.tenahub.bot.registration.SearchFilterViewSession;
import com.tenahub.bot.registration.SearchFilterViewSessionManager;
import com.tenahub.bot.registration.UpdateField;
import com.tenahub.bot.registration.UpdateSessionManager;
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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
@Service
public class TelegramCallbackHandler extends TelegramHandlerSupport {

    public TelegramCallbackHandler(
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

    public void handleCallback(TelegramUpdateDTO update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChat().getId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        String callbackId = update.getCallbackQuery().getId();
        if (handleAlertRecentCallback(chatId, messageId, data, callbackId)) return;

        if (handleFavoritesCallback(chatId, messageId, data, callbackId)) return;

        if (handleAltMedCallback(chatId, messageId, data, callbackId)) return;

        if (handleReservationBasicsCallback(chatId, messageId, data, callbackId)) return;

        if (handleMultiMedicineCallback(chatId, messageId, data, callbackId)) return;


        if (handleReservationQuantityCallback(chatId, messageId, data, callbackId)) return;

        if (handleRateAndCallCallback(chatId, messageId, data, callbackId)) return;

        if (handleMedSelectionCallback(chatId, messageId, data, callbackId)) return;

        if (handleReserveCallback(chatId, messageId, data, callbackId)) return;
        if (handleAdminCallback(chatId, messageId, data, callbackId)) return;

        if (handleTimeSelectionCallback(chatId, messageId, data, callbackId)) return;

        if (handleDetailViewCallback(chatId, messageId, data, callbackId)) return;

        telegramClient.answerCallback(callbackId);
    }

    protected boolean handleAlertRecentCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("recent_search_home")) {
    telegramClient.answerCallback(callbackId);
    restoreKeyboard(chatId);
    return true;
}
if (data.equals("alert_home")) {
    telegramClient.answerCallback(callbackId);
    restoreKeyboard(chatId);
    return true;
}
if (data.startsWith("recent_search_")) {
    telegramClient.answerCallback(callbackId);

    String medicine = data.substring("recent_search_".length());

    UserLocation loc = userLocationService.getLocation(chatId);
    if (loc == null) {
        telegramClient.sendMessage(chatId, "⚠️ Please share your location first.");
        restoreKeyboard(chatId);
        return true;
    }

    medicineSearchLogService.logSearch(chatId, medicine);

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
    results = applySearchFilter(results, SearchFilterType.NEAREST);

    if (results.isEmpty()) {
        telegramClient.sendNoResultWithAlertOption(chatId, medicine);
        return true;
    }

    telegramClient.sendMessage(
            chatId,
            "💊 <b>Pharmacies with " + medicine + "</b>\n\nSorted by: <b>Nearest</b>"
    );

sendPharmacyResultsWithTopMap(chatId, results);

    boolean allOutOfStock = results.stream().allMatch(PharmacyResponseDTO::isOutOfStock);

    if (allOutOfStock) {
        telegramClient.sendAllResultsOutOfStockNotice(chatId, medicine);
    }

   if (SearchFilterViewSessionManager.exists(chatId)) {
    SearchFilterViewSession oldFilter = SearchFilterViewSessionManager.get(chatId);
    if (oldFilter.getFilterMessageId() != null) {
        telegramClient.deleteMessage(chatId, oldFilter.getFilterMessageId());
    }
    SearchFilterViewSessionManager.remove(chatId);
}


    return true;
}
if (data.startsWith("alert_create_")) {
    telegramClient.answerCallback(callbackId);

    String medicineName = data.substring("alert_create_".length()).trim().toLowerCase();

    UserLocation loc = userLocationService.getLocation(chatId);

    try {
        medicineAvailabilityAlertService.createAlert(chatId, medicineName, loc);

        telegramClient.sendMessage(
                chatId,
                "🔔 <b>Alert created</b>\n\n" +
                MEDICINE_LABEL + medicineName + "\n" +
                "📍 Location: " + (loc != null ? "Saved nearby location" : "Any nearby pharmacy") + "\n\n" +
                "You will be notified when it becomes available."
        );
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
if (data.startsWith("alert_remove_")) {
    telegramClient.answerCallback(callbackId);

    Long alertId = Long.parseLong(data.substring("alert_remove_".length()));

    try {
        medicineAvailabilityAlertService.removeAlert(chatId, alertId);
        telegramClient.sendMessage(chatId, "❌ Alert removed.");
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
if (data.equals("alert_remove_all")) {
    telegramClient.answerCallback(callbackId);

    try {
        medicineAvailabilityAlertService.removeAllAlerts(chatId);
        telegramClient.sendMessage(chatId, "🗑 All alerts removed.");
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
if (data.startsWith("alert_search_")) {
    telegramClient.answerCallback(callbackId);

    String medicine = data.substring("alert_search_".length()).trim().toLowerCase();

    UserLocation loc = userLocationService.getLocation(chatId);
    if (loc == null) {
        telegramClient.sendLocationRequest(chatId);
        telegramClient.sendMessage(chatId, "📍 Please share your location first.");
        return true;
    }

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
    results = applySearchFilter(results, SearchFilterType.NEAREST);

    if (results.isEmpty()) {
        telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
        return true;
    }

    telegramClient.sendMessage(
            chatId,
            "💊 <b>Pharmacies with " + medicine + "</b>\n\n" +
            "Sorted by: <b>Nearest</b>"
    );
sendPharmacyResultsWithTopMap(chatId, results);

    boolean allOutOfStock = results.stream().allMatch(PharmacyResponseDTO::isOutOfStock);

    if (allOutOfStock) {
        telegramClient.sendAllResultsOutOfStockNotice(chatId, medicine);
    }

resendTrackedSearchFilter(chatId, NEAREST);
    return true;
}
if (data.equals("alert_home")) {
    telegramClient.answerCallback(callbackId);
    restoreKeyboard(chatId);
    return true;
}
        return false;
    }


    protected boolean handleFavoritesCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("fav_remove_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("fav_remove_".length()));

    try {
        favoritePharmacyService.removeFavorite(chatId, pharmacyId);
        telegramClient.editMessageRemoveButtons(chatId, messageId);
        telegramClient.sendMessage(chatId, "✅ Removed from favorites.");
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
if (data.startsWith("fav_add_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("fav_add_".length()));

    try {
        favoritePharmacyService.addFavorite(chatId, pharmacyId);
        telegramClient.sendMessage(chatId, "❤️ Pharmacy saved to favorites.");
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
if (data.startsWith("fav_remove_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("fav_remove_".length()));

    try {
        favoritePharmacyService.removeFavorite(chatId, pharmacyId);
        telegramClient.sendMessage(chatId, "🗑 Removed from favorites.");
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
    return true;
}
        return false;
    }


    protected boolean handleAltMedCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("alt_med_cancel")) {
    telegramClient.answerCallback(callbackId);
    restoreKeyboard(chatId);
    return true;
}
if (data.startsWith("alt_med_")) {
    telegramClient.answerCallback(callbackId);

    String medicine = data.substring("alt_med_".length()).trim().toLowerCase();

    UserLocation loc = userLocationService.getLocation(chatId);
    if (loc == null) {
        telegramClient.sendMessage(chatId, "⚠️ Please share your location first.");
        restoreKeyboard(chatId);
        return true;
    }

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
    results = applySearchFilter(results, SearchFilterType.NEAREST);

    if (results.isEmpty()) {
        telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
        return true;
    }

    telegramClient.sendMessage(
            chatId,
            "💊 <b>Pharmacies with " + medicine + "</b>\n\n" +
            "Sorted by: <b>Nearest</b>"
    );

sendPharmacyResultsWithTopMap(chatId, results);

    resendTrackedSearchFilter(chatId, NEAREST);
    return true;
}
        return false;
    }


    protected boolean handleReservationBasicsCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.matches("^res_qty_(other|\\d+)$")) {

    telegramClient.answerCallback(callbackId);

    if (!ReservationSessionManager.exists(chatId)) {
        telegramClient.sendMessage(chatId, "⚠️ Reservation session not found.");
        return true;
    }

    var session = ReservationSessionManager.get(chatId);

    if (data.equals("res_qty_other")) {
        session.setWaitingForCustomQuantity(true);
        telegramClient.sendMessage(
                chatId,
                "✍️ Enter quantity as a number.\n\nExample: 4"
        );
        return true;
    }

    try {
        int qty = Integer.parseInt(data.substring("res_qty_".length()));
        session.setQuantity(qty);
        session.setWaitingForCustomQuantity(false);
        session.setWaitingForName(true);

        telegramClient.sendMessage(
                chatId,
                "👤 Please enter your full name.\n\nExample:\nTeketsel Beyene"
        );
        return true;

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid quantity selection.");
        return true;
    }
}
if (data.startsWith("cancel_res_")) {
    telegramClient.answerCallback(callbackId);

    Long reservationId = Long.parseLong(data.substring("cancel_res_".length()));

    try {
        MedicineReservation cancelled = reservationService.cancelReservationByUser(chatId, reservationId);

        telegramClient.sendMessage(
                chatId,
                "✅ Reservation cancelled.\n\n" +
                        "🆔 ID: " + cancelled.getId() + "\n" +
                        MEDICINE_LABEL + cancelled.getMedicineName() + "\n" +
                        "📌 Status: " + cancelled.getStatus()
        );

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }

    return true;
}
if (data.startsWith("user_cancel_res_")) {
    telegramClient.answerCallback(callbackId);

    Long reservationId = Long.parseLong(data.substring("user_cancel_res_".length()));

    try {
        var reservation = reservationService.cancelReservationByUser(chatId, reservationId);

        telegramClient.sendMessage(
                chatId,
                "✅ Reservation cancelled.\n\n" +
                        "🆔 ID: " + reservation.getId() + "\n" +
                        MEDICINE_LABEL + reservation.getMedicineName() + "\n" +
                        QUANTITY_LABEL + reservation.getRequestedQuantity()
        );

        telegramClient.editMessageRemoveButtons(chatId, messageId);

        Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId())
                .orElse(null);

        if (pharmacy != null) {
            telegramClient.sendMessage(
                    pharmacy.getTelegramId(),
                    "❌ User cancelled a reservation.\n\n" +
                            "🆔 ID: " + reservation.getId() + "\n" +
                            MEDICINE_LABEL + reservation.getMedicineName() + "\n" +
                            QUANTITY_LABEL + reservation.getRequestedQuantity()
            );
        }

        return true;

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        return true;
    }
}
if (data.equals("res_back")) {
    telegramClient.answerCallback(callbackId);

    if (ReservationSessionManager.exists(chatId)) {
        ReservationSessionManager.remove(chatId);
    }

    restoreKeyboard(chatId);
    return true;
}
if (data.equals("res_main")) {
    telegramClient.answerCallback(callbackId);

    if (ReservationSessionManager.exists(chatId)) {
        ReservationSessionManager.remove(chatId);
    }

    MultiMedicineSearchSessionManager.remove(chatId);
    LocationSelectionSessionManager.remove(chatId);

    telegramClient.sendUserDashboard(chatId);
    return true;
}
if (data.equals("res_cancel")) {
            telegramClient.answerCallback(callbackId);

            if (ReservationSessionManager.exists(chatId)) {
                ReservationSessionManager.remove(chatId);
            }

            telegramClient.sendMessage(chatId, "❌ Reservation cancelled.");
            return true;
        }
        return false;
    }


    protected boolean handleMultiMedicineCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("multi_search")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);

            if (session.getSelectedMedicines().isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ Select at least one medicine.");
                return true;
            }

            UserLocation loc = userLocationService.getLocation(chatId);
            if (loc == null) {
                telegramClient.sendLocationRequest(chatId);
                telegramClient.sendMessage(chatId, "📍 Please share your location first.");
                return true;
            }

            var results = pharmacyService.searchMultipleMedicinesNearby(
                    session.getSelectedMedicines(),
                    loc.getLatitude(),
                    loc.getLongitude(),
                    chatId
            );

            if (results.isEmpty()) {
                telegramClient.sendMessage(chatId, "❌ No pharmacies found for the selected medicines.");
                return true;
            }

            telegramClient.sendMessage(
                    chatId,
                    "🏥 <b>Multi-Medicine Search Results</b>\n\nShowing pharmacies with the best match first."
            );
            restoreKeyboard(chatId);

            results.forEach(r -> telegramClient.sendMultiMedicinePharmacyResult(chatId, r));
            return true;
        }
if (data.equals("multi_loc_saved")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);
            session.setWaitingForLocationChoice(false);
            session.setWaitingForMedicineInput(true);

            telegramClient.sendMessage(
                    chatId,
                    "💊 Send the first medicine name.\n\nExample:\ninsulin"
            );
            return true;
        }
if (data.equals("multi_loc_share")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);
            session.setWaitingForLocationChoice(false);
            session.setWaitingForMedicineInput(true);

            telegramClient.sendLocationRequest(chatId);
            telegramClient.sendMessage(chatId, "📍 Share your location, then send medicine name.");
            return true;
        }
if (data.equals("multi_add_more")) {
            telegramClient.answerCallback(callbackId);

            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                MultiMedicineSearchSessionManager.get(chatId).setWaitingForMedicineInput(true);
                telegramClient.sendMessage(chatId, "💊 Send another medicine name.");
            }
            return true;
        }
if (data.startsWith("multi_reserve_all_later_")) {
            telegramClient.answerCallback(callbackId);

            telegramClient.sendMessage(
                    chatId,
                    "🧺 <b>Reserve All Later</b>\n\n"
                            + "Multi-medicine reservation in one request is not available yet.\n\n"
                            + "For now, please use <b>Reserve Matched</b> and reserve one matched medicine at a time."
            );
            return true;
        }
if (data.equals("multi_clear")) {
            telegramClient.answerCallback(callbackId);

            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                MultiMedicineSearchSessionManager.get(chatId).getSelectedMedicines().clear();
                telegramClient.sendMultiMedicinePanel(chatId,
                        MultiMedicineSearchSessionManager.get(chatId).getSelectedMedicines());
            }
            return true;
        }
if (data.startsWith("multi_remove_")) {
            telegramClient.answerCallback(callbackId);

            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                String med = data.substring("multi_remove_".length()).toLowerCase();
                MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);
                session.getSelectedMedicines().removeIf(x -> x.equalsIgnoreCase(med));
                telegramClient.sendMultiMedicinePanel(chatId, session.getSelectedMedicines());
            }
            return true;
        }
if (data.equals("multi_cancel")) {
            telegramClient.answerCallback(callbackId);
            MultiMedicineSearchSessionManager.remove(chatId);
            telegramClient.sendUserDashboard(chatId);
            return true;
        }
if (data.startsWith("multi_reserve_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Multi-medicine session expired.");
                return true;
            }

            Long pharmacyId = Long.parseLong(data.substring("multi_reserve_".length()));

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);

            if (session.getSelectedMedicines() == null || session.getSelectedMedicines().isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No selected medicines found.");
                return true;
            }

            Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            List<PharmacyInventory> inventoryList = inventoryRepository.findByPharmacyId(pharmacy.getId());

            List<String> matchedMedicines = session.getSelectedMedicines().stream()
                    .filter(selected -> inventoryList.stream().anyMatch(item ->
                            item.getMedicineName() != null
                                    && item.getMedicineName().equalsIgnoreCase(selected)
                                    && !item.isOutOfStock()
                                    && item.getQuantity() != null
                                    && item.getQuantity() > 0
                    ))
                    .toList();

            if (matchedMedicines.isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No matched medicines available for reservation.");
                return true;
            }

            if (matchedMedicines.size() == 1) {
    String medicineName = matchedMedicines.get(0);

    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        MultiMedicineSearchSession multiSession = MultiMedicineSearchSessionManager.get(chatId);
        multiSession.setWaitingForMedicineInput(false);
    }

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);
    telegramClient.sendReservationQuantityPicker(chatId, medicineName);
    return true;
}

            telegramClient.sendMatchedMedicineReservePicker(chatId, pharmacyId, matchedMedicines);
            return true;
        }
        return false;
    }


    protected boolean handleReservationQuantityCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("res_qty_pick_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("res_qty_pick_".length());
    String[] parts = payload.split("_");

    if (parts.length < 3) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid quantity data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(parts[0]);
    String medicineName = parts[1];
    int qty = Integer.parseInt(parts[2]);

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);
    var session = ReservationSessionManager.get(chatId);
    session.setSourceMessageId(messageId);
    session.setQuantity(qty);
    session.setWaitingForName(true);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;

    UserLocation loc = userLocationService.getLocation(chatId);
    Double distance = null;
    if (loc != null && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        distance = com.tenahub.bot.util.GeoUtils.distance(
                loc.getLatitude(),
                loc.getLongitude(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude()
        );
    }

    telegramClient.editPharmacyMessageAskName(
            chatId,
            messageId,
            pharmacy.getName(),
            pharmacy.getArea(),
            pharmacy.getPhone(),
            distance,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacyId,
            pharmacy.getRating(),
            inventory == null ? 0 : inventory.getQuantity(),
            outOfStock,
            medicineName,
            inventory == null ? null : inventory.getPrice(),
            isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
            qty
    );
    return true;
}
if (data.startsWith("close_reserve_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("close_reserve_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve close data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
PharmacyInventory inventory = inventoryRepository
        .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
        .orElse(null);

boolean outOfStock = inventory == null
        || inventory.isOutOfStock()
        || inventory.getQuantity() == null
        || inventory.getQuantity() <= 0;

boolean canRate = !ratingService.hasUserRated(pharmacyId, chatId);

telegramClient.restorePharmacyCardButtons(
        chatId,
        messageId,
        pharmacyId,
        medicineName,
        pharmacy.getLatitude(),
        pharmacy.getLongitude(),
        outOfStock,
        canRate
);
    return true;
}
if (data.startsWith("res_qty_custom_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("res_qty_custom_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid quantity data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);
    var session = ReservationSessionManager.get(chatId);
    session.setSourceMessageId(messageId);
    session.setWaitingForCustomQuantity(true);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;

    UserLocation loc = userLocationService.getLocation(chatId);
    Double distance = null;
    if (loc != null && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        distance = com.tenahub.bot.util.GeoUtils.distance(
                loc.getLatitude(),
                loc.getLongitude(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude()
        );
    }

    telegramClient.editPharmacyMessageAskCustomQuantity(
            chatId,
            messageId,
            pharmacy.getName(),
            pharmacy.getArea(),
            pharmacy.getPhone(),
            distance,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacyId,
            pharmacy.getRating(),
            inventory == null ? 0 : inventory.getQuantity(),
            outOfStock,
            medicineName,
            inventory == null ? null : inventory.getPrice(),
            isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString()
    );
    return true;
}
if (data.startsWith("res_qty_inline_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("res_qty_inline_".length());
    String[] parts = payload.split("_");

    if (parts.length < 3) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve quantity data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(parts[0]);
    String medicineName = parts[1];
    String qtyPart = parts[2];

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);

    var session = ReservationSessionManager.get(chatId);

    if ("other".equalsIgnoreCase(qtyPart)) {
        session.setWaitingForCustomQuantity(true);
        telegramClient.sendMessage(chatId, "✍️ Enter quantity as a number.\n\nExample: 4");
        return true;
    }

    try {
        int qty = Integer.parseInt(qtyPart);
        session.setQuantity(qty);
        session.setWaitingForName(true);

        telegramClient.sendMessage(
                chatId,
                "👤 Please enter your full name.\n\nExample:\nTeketsel Beyene"
        );
        return true;
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid quantity.");
        return true;
    }
}
if (data.startsWith("toggle_reserve_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("toggle_reserve_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory item = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    Integer stockQuantity = item != null && item.getQuantity() != null ? item.getQuantity() : 0;
    boolean outOfStock = item == null || item.isOutOfStock() || stockQuantity <= 0;

    boolean canRate = !ratingService.hasUserRated(pharmacyId, chatId);

    telegramClient.editPharmacyMessageToggleReserve(
            chatId,
            messageId,
            pharmacy.getName(),
            pharmacy.getArea(),
            pharmacy.getPhone(),
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacyId,
            pharmacy.getRating(),
            canRate,
            stockQuantity,
            outOfStock,
            medicineName,
            item != null ? item.getPrice() : null,
            isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
            true
    );
    return true;
}
        return false;
    }


    protected boolean handleRateAndCallCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("cancel_rate_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("cancel_rate_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid cancel action.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;

    telegramClient.restoreRateButtonAfterCancel(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            outOfStock
    );
    return true;
}
if (data.startsWith("show_rate_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("show_rate_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid rating request.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;

    telegramClient.editPharmacyMessageToRatingPicker(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            outOfStock
    );
    return true;
}
if (data.equals("cancel_rate")) {
    telegramClient.answerCallback(callbackId);
    return true;
}
if (data.startsWith("call_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("call_".length()));

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    String phone = pharmacy.getPhone() == null ? "N/A" : pharmacy.getPhone().trim();

    telegramClient.sendMessage(
            chatId,
            "📞 <b>Call Pharmacy</b>\n\n" +
            "🏥 <b>" + pharmacy.getName() + "</b>\n" +
            "📱 " + phone + "\n\n" +
            "Tap the phone number in the pharmacy card above to call."
    );
    return true;
}
if (data.startsWith("show_rate_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("show_rate_".length()));
    telegramClient.sendRatingPicker(chatId, pharmacyId);
    return true;
}
        return false;
    }


    protected boolean handleMedSelectionCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("multi_pick_reserve_cancel")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "❌ Reservation selection cancelled.");
            return true;
        }
if (data.startsWith("multi_pick_reserve_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("multi_pick_reserve_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve selection.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        MultiMedicineSearchSessionManager.get(chatId).setWaitingForMedicineInput(false);
    }

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);
    telegramClient.sendReservationQuantityPicker(chatId, medicineName);
    return true;
}
if (data.startsWith("med_")) {

            telegramClient.answerCallback(callbackId);

            if (!MedicineSelectionSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Medicine selection session not found.");
                return true;
            }

            MedicineSelectionSession medSession = MedicineSelectionSessionManager.get(chatId);

            if (medSession.getPickerMessageId() == null) {
                medSession.setPickerMessageId(messageId);
            }

            if (data.startsWith("med_toggle_")) {
                String medicine = data.substring("med_toggle_".length()).toLowerCase();

                if (medSession.getSelectedMedicines().contains(medicine)) {
                    medSession.getSelectedMedicines().remove(medicine);
                } else {
                    medSession.getSelectedMedicines().add(medicine);
                }

                medSession.setWaitingCustomInput(false);

                telegramClient.editMedicinePicker(
                        chatId,
                        medSession.getPickerMessageId(),
                        medSession.getSelectedMedicines()
                );
                return true;
            }

            if (data.startsWith("med_pick_")) {
                String medicine = data.substring("med_pick_".length()).toLowerCase();

                if (!medSession.getSelectedMedicines().contains(medicine)) {
                    medSession.getSelectedMedicines().add(medicine);
                }

                medSession.setWaitingCustomInput(false);

                telegramClient.editMedicinePicker(
                        chatId,
                        medSession.getPickerMessageId(),
                        medSession.getSelectedMedicines()
                );
                return true;
            }

            if (data.equals("med_clear")) {
                medSession.getSelectedMedicines().clear();
                medSession.setWaitingCustomInput(false);

                telegramClient.editMedicinePicker(
                        chatId,
                        medSession.getPickerMessageId(),
                        medSession.getSelectedMedicines()
                );
                return true;
            }

            if (data.equals("med_custom")) {
                medSession.setWaitingCustomInput(true);
                telegramClient.sendMessage(chatId, "✍️ Type the medicine name you want to add.");
                return true;
            }

            if (data.equals("med_custom_cancel")) {
                medSession.setWaitingCustomInput(false);

                telegramClient.editMedicinePicker(
                        chatId,
                        medSession.getPickerMessageId(),
                        medSession.getSelectedMedicines()
                );
                return true;
            }

            if (data.equals("med_cancel")) {
                MedicineSelectionSessionManager.remove(chatId);

                if (UpdateSessionManager.exists(chatId) &&
                        UpdateSessionManager.get(chatId).getField() == UpdateField.MEDICINES) {
                    UpdateSessionManager.remove(chatId);
                }

                telegramClient.sendMessage(chatId, "❌ Medicine selection cancelled.");
                return true;
            }

            if (data.equals("med_done")) {
                String medicines = String.join(",", medSession.getSelectedMedicines());

                if (medicines.isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Please select at least one medicine.");
                    return true;
                }

                if (medSession.isForRegistration()) {
                    if (RegistrationSessionManager.exists(chatId)) {
                        RegistrationSession session = RegistrationSessionManager.get(chatId);
                        session.setMedicines(medicines);
                        session.setStep(RegistrationStep.LOCATION);

                        MedicineSelectionSessionManager.remove(chatId);
                        telegramClient.sendRegistrationLocationChoice(chatId);
                        return true;
                    }
                } else {
                    pharmacyService.updateMedicines(chatId, medicines);

                    MedicineSelectionSessionManager.remove(chatId);
                    UpdateSessionManager.remove(chatId);

                    telegramClient.sendMessage(chatId, "✅ Medicines updated");
                    return true;
                }
            }

            telegramClient.sendMessage(chatId, "⚠️ Unknown medicine action.");
            return true;
        }
        return false;
    }


    protected boolean handleAdminCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("approve_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("approve_res_".length()));

            try {
                var reservation = reservationService.approveReservation(reservationId);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

                String holdUntil = reservation.getExpiresAt() == null
                        ? "N/A"
                        : reservation.getExpiresAt().format(formatter);

                telegramClient.sendMessage(
                        reservation.getUserId(),
                        "✅ Your reservation was approved.\n\n"
                                + MEDICINE_LABEL + reservation.getMedicineName() + "\n"
                                + QUANTITY_LABEL + reservation.getRequestedQuantity() + "\n"
                                + "⏳ Hold until: " + holdUntil + "\n\n"
                                + "Please arrive before the deadline."
                );

                telegramClient.editReservationToFulfilledOnly(chatId, messageId, reservationId);
                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("admin_reg_page_")) {

            telegramClient.answerCallback(callbackId);

            int page = Integer.parseInt(data.substring("admin_reg_page_".length()));

            telegramClient.sendAdminPendingRegistrationsPage(
                    chatId,
                    adminService.getPendingRegistrationsPage(page, 10),
                    page
            );
            return true;
        }
if (data.startsWith("admin_license_page_")) {

            telegramClient.answerCallback(callbackId);

            int page = Integer.parseInt(data.substring("admin_license_page_".length()));

            telegramClient.sendAdminPendingLicenseUpdatesPage(
                    chatId,
                    adminService.getPendingLicenseUpdatesPage(page, 10),
                    page
            );
            return true;
        }
if (data.equals("admin_more_pending_res")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewReservationsByStatus(MedicineReservationStatus.PENDING));
            return true;
        }
if (data.equals("admin_more_approved_res")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewReservationsByStatus(MedicineReservationStatus.APPROVED));
            return true;
        }
if (data.equals("admin_more_fulfilled_res")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewReservationsByStatus(MedicineReservationStatus.FULFILLED));
            return true;
        }
if (data.equals("admin_more_rejected_res")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewReservationsByStatus(MedicineReservationStatus.REJECTED));
            return true;
        }
if (data.equals("admin_more_expired_res")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewReservationsByStatus(MedicineReservationStatus.EXPIRED));
            return true;
        }
if (data.equals("admin_more_reservations")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendAdminReservationOversight(
                    chatId,
                    adminService.viewDetailedReservationOversight()
            );
            return true;
        }
if (data.equals("admin_more_pharmacies")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewPharmacyDetails());
            return true;
        }
if (data.equals("admin_more_top_medicines")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewTopMedicinesDetails());
            return true;
        }
if (data.equals("admin_more_low_stock")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, adminService.viewLowStockDetails());
            return true;
        }
if (data.startsWith("reject_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("reject_res_".length()));

            try {
                var reservation = reservationService.rejectReservation(reservationId, "Rejected by pharmacy");

                telegramClient.sendMessage(chatId, "❌ Reservation rejected.");
                telegramClient.sendMessage(
                        reservation.getUserId(),
                        "❌ Your reservation was rejected.\n\n"
                                + MEDICINE_LABEL + reservation.getMedicineName() + "\n"
                                + QUANTITY_LABEL + reservation.getRequestedQuantity()
                );

                telegramClient.editMessageRemoveButtons(chatId, messageId);
                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("fulfill_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("fulfill_res_".length()));

            try {
                var reservation = reservationService.fulfillReservation(reservationId);

                telegramClient.sendMessage(chatId, "📦 Reservation marked fulfilled.");
                telegramClient.sendMessage(
                        reservation.getUserId(),
                        "📦 Your reservation has been fulfilled.\n\n"
                                + MEDICINE_LABEL + reservation.getMedicineName() + "\n"
                                + QUANTITY_LABEL + reservation.getRequestedQuantity()
                );

                telegramClient.editMessageRemoveButtons(chatId, messageId);
                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("close_reg_")) {

            telegramClient.answerCallback(callbackId);

            String[] parts = data.split("_");

            if (parts.length < 4) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid close registration data.");
                return true;
            }

            Integer detailMessageId = Integer.parseInt(parts[2]);
            Long registrationId = Long.parseLong(parts[3]);

            telegramClient.deleteMessage(chatId, detailMessageId);
            telegramClient.editAdminRegistrationSummaryButtonClosed(chatId, messageId, registrationId);

            if (AdminViewSessionManager.exists(chatId)) {
                AdminViewSession current = AdminViewSessionManager.get(chatId);

                if ("REGISTRATION".equals(current.getType())
                        && registrationId.equals(current.getTargetId())
                        && messageId.equals(current.getSummaryMessageId())) {
                    AdminViewSessionManager.remove(chatId);
                }
            }

            return true;
        }
if (data.startsWith("close_license_")) {

            telegramClient.answerCallback(callbackId);

            String[] parts = data.split("_");

            if (parts.length < 4) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid close license data.");
                return true;
            }

            Integer detailMessageId = Integer.parseInt(parts[2]);
            Long pharmacyId = Long.parseLong(parts[3]);

            telegramClient.deleteMessage(chatId, detailMessageId);
            telegramClient.editAdminLicenseSummaryButtonClosed(chatId, messageId, pharmacyId);

            if (AdminViewSessionManager.exists(chatId)) {
                AdminViewSession current = AdminViewSessionManager.get(chatId);

                if ("LICENSE".equals(current.getType())
                        && pharmacyId.equals(current.getTargetId())
                        && messageId.equals(current.getSummaryMessageId())) {
                    AdminViewSessionManager.remove(chatId);
                }
            }

            return true;
        }
if (data.startsWith("view_license_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring("view_license_".length()));
            Integer summaryMessageId = messageId;

            try {
                if (AdminViewSessionManager.exists(chatId)) {
                    AdminViewSession current = AdminViewSessionManager.get(chatId);

                    if (current.getDetailMessageId() != null) {
                        telegramClient.deleteMessage(chatId, current.getDetailMessageId());
                    }

                    if ("LICENSE".equals(current.getType())) {
                        telegramClient.editAdminLicenseSummaryButtonClosed(
                                chatId,
                                current.getSummaryMessageId(),
                                current.getTargetId()
                        );
                    } else if ("REGISTRATION".equals(current.getType())) {
                        telegramClient.editAdminRegistrationSummaryButtonClosed(
                                chatId,
                                current.getSummaryMessageId(),
                                current.getTargetId()
                        );
                    }

                    AdminViewSessionManager.remove(chatId);
                }

                Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                if (pharmacy.getPendingLicenseFileId() == null || pharmacy.getPendingLicenseFileId().isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Pending license file not found.");
                    return true;
                }

                String caption = "🔄 <b>License Update Request</b>\n\n"
                        + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                        + "🏙️ <b>City:</b> " + pharmacy.getCity() + "\n"
                        + "📍 <b>Area:</b> " + pharmacy.getArea() + "\n"
                        + "📞 <b>Phone:</b> " + pharmacy.getPhone() + "\n"
                        + "💊 <b>Medicines:</b> " + pharmacy.getMedicines() + "\n"
                        + "🕒 <b>Open:</b> " + pharmacy.getOpenTime() + "\n"
                        + "🌙 <b>Close:</b> " + pharmacy.getCloseTime() + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                Integer detailMessageId = telegramClient.sendPhotoWithLicenseUpdateButtons(
                        chatId,
                        pharmacy.getPendingLicenseFileId(),
                        caption,
                        pharmacy.getTelegramId()
                );

                if (detailMessageId == null) {
                    telegramClient.sendMessage(chatId, "⚠️ Could not open license detail.");
                    return true;
                }

                telegramClient.editAdminLicenseSummaryButtonOpen(
                        chatId,
                        summaryMessageId,
                        pharmacyId,
                        detailMessageId
                );

                AdminViewSessionManager.save(
                        chatId,
                        new AdminViewSession("LICENSE", pharmacyId, summaryMessageId, detailMessageId)
                );

                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("view_reg_")) {

            telegramClient.answerCallback(callbackId);

            Long registrationId = Long.parseLong(data.substring("view_reg_".length()));
            Integer summaryMessageId = messageId;

            try {
                if (AdminViewSessionManager.exists(chatId)) {
                    AdminViewSession current = AdminViewSessionManager.get(chatId);

                    if (current.getDetailMessageId() != null) {
                        telegramClient.deleteMessage(chatId, current.getDetailMessageId());
                    }

                    if ("REGISTRATION".equals(current.getType())) {
                        telegramClient.editAdminRegistrationSummaryButtonClosed(
                                chatId,
                                current.getSummaryMessageId(),
                                current.getTargetId()
                        );
                    } else if ("LICENSE".equals(current.getType())) {
                        telegramClient.editAdminLicenseSummaryButtonClosed(
                                chatId,
                                current.getSummaryMessageId(),
                                current.getTargetId()
                        );
                    }

                    AdminViewSessionManager.remove(chatId);
                }

                var reg = registrationService.getRegistration(registrationId);

                if (reg.getLicenseFileId() == null || reg.getLicenseFileId().isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Registration license file not found.");
                    return true;
                }

                String caption = "🆕 <b>New Pharmacy Registration</b>\n\n"
                        + "🆔 <b>ID:</b> " + reg.getId() + "\n"
                        + "🏥 <b>Name:</b> " + reg.getName() + "\n"
                        + "🏙️ <b>City:</b> " + reg.getCity() + "\n"
                        + "📍 <b>Area:</b> " + reg.getArea() + "\n"
                        + "📞 <b>Phone:</b> " + reg.getPhone() + "\n"
                        + "💊 <b>Medicines:</b> " + reg.getMedicines() + "\n"
                        + "🕒 <b>Open:</b> " + reg.getOpenTime() + "\n"
                        + "🌙 <b>Close:</b> " + reg.getCloseTime() + "\n"
                        + "📌 <b>Latitude:</b> " + reg.getLatitude() + "\n"
                        + "📌 <b>Longitude:</b> " + reg.getLongitude() + "\n"
                        + "👤 <b>Telegram ID:</b> " + reg.getTelegramId();

                Integer detailMessageId = telegramClient.sendPhotoWithButtons(
                        chatId,
                        reg.getLicenseFileId(),
                        caption,
                        reg.getId()
                );

                if (detailMessageId == null) {
                    telegramClient.sendMessage(chatId, "⚠️ Could not open registration detail.");
                    return true;
                }

                telegramClient.editAdminRegistrationSummaryButtonOpen(
                        chatId,
                        summaryMessageId,
                        registrationId,
                        detailMessageId
                );

                AdminViewSessionManager.save(
                        chatId,
                        new AdminViewSession("REGISTRATION", registrationId, summaryMessageId, detailMessageId)
                );

                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("approve_license_") || data.startsWith("reject_license_")) {

            telegramClient.answerCallback(callbackId);

            String[] parts = data.split("_");

            if (parts.length < 3) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid license callback data.");
                return true;
            }

            Long pharmacyTelegramId = Long.parseLong(parts[2]);

            try {
                if (data.startsWith("approve_license_")) {
                    pharmacyService.approvePendingLicenseUpdate(pharmacyTelegramId);

                    telegramClient.sendMessage(chatId, "✅ License update approved.");
                    telegramClient.sendMessage(
                            pharmacyTelegramId,
                            "✅ Your new license has been approved by admin."
                    );

                    telegramClient.editMessageRemoveButtons(chatId, messageId);
                    return true;
                } else {
                    AdminRejectSessionManager.start(
                            chatId,
                            AdminRejectType.LICENSE_UPDATE,
                            pharmacyTelegramId
                    );

                    telegramClient.sendMessage(
                            chatId,
                            "✍️ Send rejection reason for this license update."
                    );
                    return true;
                }

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("delete_reg_")) {
    telegramClient.answerCallback(callbackId);

    Long registrationId = Long.parseLong(data.substring("delete_reg_".length()));

    try {
        PharmacyRegistration reg = registrationService.getRegistration(registrationId);

        registrationService.deleteRegistration(registrationId);

        telegramClient.sendMessage(
                chatId,
                "🗑 Registration deleted.\n\n"
                        + "ID: " + registrationId + "\n"
                        + "Telegram ID: " + reg.getTelegramId()
        );

        telegramClient.editMessageRemoveButtons(chatId, messageId);
        return true;

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        return true;
    }
}
if (data.startsWith("approve_") || data.startsWith("reject_")) {

            String[] parts = data.split("_");

            if (parts.length < 2) {
                telegramClient.answerCallback(callbackId);
                telegramClient.sendMessage(chatId, "⚠️ Invalid callback data.");
                return true;
            }

            Long registrationId = Long.parseLong(parts[1]);

            if (!registrationService.existsById(registrationId)) {
                telegramClient.answerCallback(callbackId);
                telegramClient.sendMessage(chatId, "⚠️ Registration not found.");
                return true;
            }

            if (registrationService.isProcessed(registrationId)) {
                telegramClient.answerCallback(callbackId);
                telegramClient.sendMessage(chatId, "⚠️ This registration was already processed.");
                return true;
            }

            if (data.startsWith("approve_")) {
                Long userId = registrationService.approve(registrationId);

                telegramClient.sendMessage(chatId, "✅ Pharmacy approved");
                telegramClient.sendMessage(
                        userId,
                        "✅ Your pharmacy registration has been approved."
                );

                telegramClient.editMessageRemoveButtons(chatId, messageId);
                telegramClient.answerCallback(callbackId);
                return true;
            }

            if (data.startsWith("reject_")) {
                telegramClient.answerCallback(callbackId);

                AdminRejectSessionManager.start(
                        chatId,
                        AdminRejectType.REGISTRATION,
                        registrationId
                );

                telegramClient.sendMessage(
                        chatId,
                        "✍️ Send rejection reason for this registration."
                );

                return true;
            }
        }
        return false;
    }


    protected boolean handleReserveCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("reserve_")) {

    telegramClient.answerCallback(callbackId);

    String[] parts = data.split("_", 3);

    if (parts.length < 3) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reservation data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(parts[1]);
    String medicineName = parts[2];

    ReservationSessionManager.start(chatId, pharmacyId, medicineName);
    telegramClient.sendReservationQuantityPicker(chatId, medicineName);
    return true;
}
        return false;
    }


    protected boolean handleTimeSelectionCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("time_")) {

            telegramClient.answerCallback(callbackId);

            String[] parts = data.split("_");

            if (parts.length < 5) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid time selection.");
                return true;
            }

            String mode = parts[1];
            String phase = parts[2];
            String kind = parts[3];

            if ("reg".equals(mode)) {

                if (!RegistrationSessionManager.exists(chatId)) {
                    telegramClient.sendMessage(chatId, "⚠️ Registration session not found.");
                    return true;
                }

                RegistrationSession session = RegistrationSessionManager.get(chatId);

                if ("hour".equals(kind)) {
                    int hour = Integer.parseInt(parts[4]);
                    session.setTempHour(hour);

                    if ("open".equals(phase)) {
                        session.setStep(RegistrationStep.OPEN_MINUTE);
                    } else {
                        session.setStep(RegistrationStep.CLOSE_MINUTE);
                    }

                    telegramClient.sendMinutePicker(
                            chatId,
                            "Select " + phase + " minute",
                            "reg_" + phase,
                            hour
                    );
                    return true;
                }

                if ("minute".equals(kind)) {
                    if (parts.length < 6) {
                        telegramClient.sendMessage(chatId, "⚠️ Invalid minute selection.");
                        return true;
                    }

                    String hour = parts[4];
                    String minute = parts[5];
                    String fullTime = hour + ":" + minute;

                    if ("open".equals(phase)) {
                        session.setOpenTime(fullTime);
                        session.setTempHour(null);
                        session.setStep(RegistrationStep.CLOSE_HOUR);

                        telegramClient.sendMessage(chatId, "✅ Opening time set to " + fullTime);
                        telegramClient.sendHourPicker(chatId, "Step 4/7\n🌙 Select closing hour", "reg_close");
                        return true;
                    }

                    if ("close".equals(phase)) {
                        session.setCloseTime(fullTime);
                        session.setTempHour(null);
                        session.setStep(RegistrationStep.MEDICINES);

                        telegramClient.sendMessage(chatId, "✅ Closing time set to " + fullTime);

                        MedicineSelectionSessionManager.start(chatId, true);
                        telegramClient.sendMedicinePicker(chatId, List.of());
                        return true;
                    }
                }
            }

            if ("update".equals(mode)) {

                if (!UpdateSessionManager.exists(chatId)) {
                    telegramClient.sendMessage(chatId, "⚠️ Update session not found.");
                    return true;
                }

                UpdateSession session = UpdateSessionManager.get(chatId);

                if (session.getField() != UpdateField.HOURS) {
                    telegramClient.sendMessage(chatId, "⚠️ Hours update session not active.");
                    return true;
                }

                if ("hour".equals(kind)) {
                    int hour = Integer.parseInt(parts[4]);
                    session.setTempHour(hour);

                    telegramClient.sendMinutePicker(
                            chatId,
                            "Select " + phase + " minute",
                            "update_" + phase,
                            hour
                    );
                    return true;
                }

                if ("minute".equals(kind)) {
                    if (parts.length < 6) {
                        telegramClient.sendMessage(chatId, "⚠️ Invalid minute selection.");
                        return true;
                    }

                    String hour = parts[4];
                    String minute = parts[5];
                    String fullTime = hour + ":" + minute;

                    if ("open".equals(phase)) {
                        session.setOpenTime(fullTime);
                        session.setTempHour(null);

                        telegramClient.sendMessage(chatId, "✅ Opening time selected: " + fullTime);
                        telegramClient.sendHourPicker(chatId, "🌙 Select closing hour", "update_close");
                        return true;
                    }

                    if ("close".equals(phase)) {
                        String openTime = session.getOpenTime();
                        String closeTime = fullTime;

                        pharmacyService.updateHours(chatId, openTime, closeTime);

                        telegramClient.sendMessage(
                                chatId,
                                "✅ Working hours updated\n\nOpen: " + openTime + "\nClose: " + closeTime
                        );

                        UpdateSessionManager.remove(chatId);
                        return true;
                    }
                }
            }

            telegramClient.sendMessage(chatId, "⚠️ Unsupported time action.");
            return true;
        }
        return false;
    }


    protected boolean handleDetailViewCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("details_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("details_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid details request.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory item = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    UserLocation loc = userLocationService.getLocation(chatId);
    double distance = 0.0;
    if (loc != null && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        distance = com.tenahub.bot.util.GeoUtils.distance(
                loc.getLatitude(),
                loc.getLongitude(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude()
        );
    }

    boolean canRate = !ratingService.hasUserRated(pharmacyId, chatId);
    boolean outOfStock = item == null || item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0;
    Integer stockQuantity = item == null ? 0 : item.getQuantity();
    BigDecimal price = item == null ? null : item.getPrice();

    String fullAddress = buildFullAddress(pharmacy);

    String lastStockUpdate = null;
    if (item != null && item.getUpdatedAt() != null) {
        lastStockUpdate = item.getUpdatedAt().format(
                java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
        );
    }

    // delete old photo
    if (PharmacyDetailViewSessionManager.exists(chatId)) {
        PharmacyDetailViewSession oldSession = PharmacyDetailViewSessionManager.get(chatId);
        if (oldSession.getPhotoMessageId() != null) {
            telegramClient.deleteMessage(chatId, oldSession.getPhotoMessageId());
        }
        PharmacyDetailViewSessionManager.remove(chatId);
    }

    // delete old filter FIRST
    if (SearchFilterViewSessionManager.exists(chatId)) {
        SearchFilterViewSession oldFilter = SearchFilterViewSessionManager.get(chatId);
        if (oldFilter.getFilterMessageId() != null) {
            telegramClient.deleteMessage(chatId, oldFilter.getFilterMessageId());
        }
        SearchFilterViewSessionManager.remove(chatId);
    }

    // 1. details card
    telegramClient.editPharmacyMessageToDetails(
            chatId,
            messageId,
            pharmacy.getName(),
            fullAddress,
            pharmacy.getFormattedAddress(),
            pharmacy.getLandmark(),
            pharmacy.getPlusCode(),
            pharmacy.getPhone(),
            distance,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacyId,
            pharmacy.getRating(),
            canRate,
            stockQuantity,
            outOfStock,
            medicineName,
            price,
            pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
            lastStockUpdate
    );

    // 2. photo
    Integer photoMessageId = null;
    if (pharmacy.getPhotoFileId() != null && !pharmacy.getPhotoFileId().isBlank()) {
        photoMessageId = telegramClient.sendPharmacyPhotoWithMessageId(
                chatId,
                pharmacy.getPhotoFileId(),
                "🖼 <b>" + pharmacy.getName() + "</b>\n📍 " + fullAddress
        );
    }

    PharmacyDetailViewSessionManager.save(
            chatId,
            new PharmacyDetailViewSession(photoMessageId)
    );

    // 3. filter LAST
    resendTrackedSearchFilter(chatId, NEAREST);

    return true;
}
if (data.startsWith("hide_details_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("hide_details_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid hide details request.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory item = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    UserLocation loc = userLocationService.getLocation(chatId);
    double distance = 0.0;
    if (loc != null && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        distance = com.tenahub.bot.util.GeoUtils.distance(
                loc.getLatitude(),
                loc.getLongitude(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude()
        );
    }

    boolean canRate = !ratingService.hasUserRated(pharmacyId, chatId);
    boolean outOfStock = item == null || item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0;
    Integer stockQuantity = item == null ? 0 : item.getQuantity();
    BigDecimal price = item == null ? null : item.getPrice();

    // delete photo
    if (PharmacyDetailViewSessionManager.exists(chatId)) {
        PharmacyDetailViewSession detailSession = PharmacyDetailViewSessionManager.get(chatId);
        if (detailSession.getPhotoMessageId() != null) {
            telegramClient.deleteMessage(chatId, detailSession.getPhotoMessageId());
        }
        PharmacyDetailViewSessionManager.remove(chatId);
    }

    telegramClient.editPharmacyMessageToCompact(
            chatId,
            messageId,
            pharmacy.getName(),
            pharmacy.getArea(),
            pharmacy.getPhone(),
            distance,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacyId,
            pharmacy.getRating(),
            canRate,
            stockQuantity,
            outOfStock,
            medicineName,
            price,
            pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString()
    );

    // filter LAST again
    resendTrackedSearchFilter(chatId, NEAREST);

    return true;
}
if (data.startsWith("rate_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("rate_".length());
    int lastUnderscore = payload.lastIndexOf("_");

    if (lastUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid rating data.");
        return true;
    }

    String beforeRating = payload.substring(0, lastUnderscore);
    int firstUnderscore = beforeRating.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid rating data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(beforeRating.substring(0, firstUnderscore));
    String medicineName = beforeRating.substring(firstUnderscore + 1);
    int rating = Integer.parseInt(payload.substring(lastUnderscore + 1));

    try {
        ratingService.ratePharmacy(pharmacyId, chatId, rating);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

        PharmacyInventory inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
                .orElse(null);

        boolean outOfStock = inventory == null
                || inventory.isOutOfStock()
                || inventory.getQuantity() == null
                || inventory.getQuantity() <= 0;

        telegramClient.sendMessage(chatId, "⭐ Thanks for rating " + rating + "/5");

       telegramClient.restoreNormalPharmacyButtonsAfterRating(
        chatId,
        messageId,
        pharmacyId,
        medicineName,
        pharmacy.getLatitude(),
        pharmacy.getLongitude(),
        pharmacy.getPhone(),
        outOfStock
);

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ You already rated this pharmacy.");
    }

    return true;
}
        return false;
    }


}
