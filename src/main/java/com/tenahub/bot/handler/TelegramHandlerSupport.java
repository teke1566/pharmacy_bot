package com.tenahub.bot.handler;
import com.tenahub.bot.registration.LocationFlowType;
import com.tenahub.bot.dto.EthiopiaLocationOption;
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
import com.tenahub.bot.registration.LocationSelectionSession;
import com.tenahub.bot.registration.LocationSelectionSessionManager;
import com.tenahub.bot.registration.MedicineSearchSession;
import com.tenahub.bot.registration.MedicineSearchSessionManager;
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
import com.tenahub.bot.registration.UpdateSession;
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
import com.tenahub.bot.util.EthiopiaLocationCatalog;
import com.tenahub.bot.util.TelegramClient;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared dependencies, constants, and helper flows for Telegram message/callback handlers.
 */
public abstract class TelegramHandlerSupport {

    protected final PharmacyService pharmacyService;
    protected final RegistrationService registrationService;
    protected final TelegramClient telegramClient;
    protected final UserLocationService userLocationService;
    protected final RatingService ratingService;
    protected final PharmacyRepository pharmacyRepository;
    protected final InventoryService inventoryService;
    protected final ReservationService reservationService;
    protected final AdminService adminService;
    protected final PharmacyInventoryRepository inventoryRepository;
    protected final FavoritePharmacyService favoritePharmacyService;
    protected final MedicineSearchLogService medicineSearchLogService;
    protected final MedicineAvailabilityAlertService medicineAvailabilityAlertService;

    protected final Long ADMIN_CHAT_ID = 8251771745L;

    protected static final String PHARMACY_NOT_FOUND = "Pharmacy not found";
    protected static final String WARN_PREFIX = "⚠️ ";
    protected static final String MEDICINE_LABEL = "💊 Medicine: ";
    protected static final String QUANTITY_LABEL = "🔢 Quantity: ";
    protected static final String BACK_ARROW = "⬅️ back";
    protected static final String BACK_SYMBOL = "🔙 back";
    protected static final String MAIN_HOME = "🏠 main";
    protected static final String NEAREST = "Nearest";
    protected static final String PHONE_LABEL = "📱 Phone: ";
    protected static final String PENDING = "PENDING";

    protected TelegramHandlerSupport(
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
            MedicineAvailabilityAlertService medicineAvailabilityAlertService) {
        this.pharmacyService = pharmacyService;
        this.registrationService = registrationService;
        this.telegramClient = telegramClient;
        this.userLocationService = userLocationService;
        this.ratingService = ratingService;
        this.pharmacyRepository = pharmacyRepository;
        this.inventoryService = inventoryService;
        this.reservationService = reservationService;
        this.adminService = adminService;
        this.inventoryRepository = inventoryRepository;
        this.favoritePharmacyService = favoritePharmacyService;
        this.medicineSearchLogService = medicineSearchLogService;
        this.medicineAvailabilityAlertService = medicineAvailabilityAlertService;
    }

protected void handleContactMessage(TelegramUpdateDTO update, Long chatId) {

    if (RegistrationSessionManager.exists(chatId)) {
        
        RegistrationSession regSession = RegistrationSessionManager.get(chatId);

        if (regSession.getStep() == RegistrationStep.PHONE) {
            String phone = update.getMessage().getContact().getPhoneNumber();

            if (phone == null || phone.isBlank()) {
                telegramClient.sendMessage(chatId, "⚠️ Could not read shared phone number.");
                return;
            }

            regSession.setPhone(phone.replaceAll("\\s+", ""));
            regSession.setStep(RegistrationStep.OPEN_HOUR);

            telegramClient.sendHourPicker(
                    chatId,
                    "Step 3/7\n⏰ Select opening hour",
                    "reg_open"
            );
            return;
        }
    }

    if (!ReservationSessionManager.exists(chatId)) {
        return;
    }

    var session = ReservationSessionManager.get(chatId);

    if (!session.isWaitingForPhone() || session.getQuantity() == null) {
        return;
    }

    String phone = update.getMessage().getContact().getPhoneNumber();

    try {
        var reservation = reservationService.createReservation(
                chatId,
                session.getPharmacyId(),
                session.getMedicineName(),
                session.getQuantity(),
                phone,
                session.getCustomerName()
        );

        Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

        try {
            telegramClient.sendReservationRequestToPharmacy(
                    pharmacy.getTelegramId(),
                    reservation.getId(),
                    chatId,
                    session.getMedicineName(),
                    session.getQuantity(),
                    phone,
                    session.getCustomerName()
            );

            telegramClient.sendMessageRemoveKeyboard(
                    chatId,
                    "✅ Reservation request sent to pharmacy.\n\n"
                            + MEDICINE_LABEL + session.getMedicineName() + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + phone + "\n"
                            + "🕒 Waiting for pharmacy approval."
            );

            restoreKeyboard(chatId);

        } catch (Exception notifyError) {
            telegramClient.sendMessageRemoveKeyboard(
                    chatId,
                    "✅ Reservation saved.\n\n"
                            + MEDICINE_LABEL + session.getMedicineName() + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + phone + "\n\n"
                            + "⚠️ Could not notify the pharmacy automatically."
            );

            restoreKeyboard(chatId);
        }

    } catch (Exception createError) {
        telegramClient.sendMessageRemoveKeyboard(
                chatId,
                "⚠️ Could not create reservation.\n\n" + createError.getMessage()
        );

        restoreKeyboard(chatId);
    } finally {
        ReservationSessionManager.remove(chatId);
    }
}
protected void handleLocationMessage(TelegramUpdateDTO update, Long chatId) {
    Double lat = update.getMessage().getLocation().getLatitude();
    Double lon = update.getMessage().getLocation().getLongitude();

    if (lat == null || lon == null) {
        telegramClient.sendMessage(chatId, "⚠️ Could not read location.");
        return;
    }

    try {
        // 1) REGISTRATION EXACT LOCATION - MUST COME FIRST
        if (RegistrationSessionManager.exists(chatId)) {
    RegistrationSession session = RegistrationSessionManager.get(chatId);

    if (session != null
            && session.getStep() == RegistrationStep.LOCATION
            && session.isWaitingForExactLocation()) {

        session.setLatitude(lat);
        session.setLongitude(lon);
        session.clearLocationFlags();

        EthiopiaLocationOption nearest = EthiopiaLocationCatalog.findNearest(lat, lon);

        if (shouldUseEthiopiaCatalog(lat, lon, nearest)) {
            session.setSelectedRegion(nearest.getRegion());
            session.setSelectedCity(nearest.getCity());
            session.setCity(nearest.getCity());
            session.setArea(nearest.getArea());

            if (EthiopiaLocationCatalog.isAddisAbabaRegion(nearest.getRegion())) {
                session.setSelectedSubCity(
                        EthiopiaLocationCatalog.findAddisSubCityByArea(nearest.getArea())
                );
            }

            session.setFormattedAddress(nearest.getArea() + ", " + nearest.getCity());

            userLocationService.saveLocation(
                    chatId,
                    lat,
                    lon,
                    session.getSelectedRegion(),
                    session.getSelectedCity(),
                    session.getSelectedSubCity(),
                    session.getArea(),
                    session.getFormattedAddress()
            );

            telegramClient.sendLocation(chatId, lat, lon);
            finalizeRegistrationLocation(chatId, session);
        } else {
            // real location saved, but do not fake Ethiopia details
            session.setSelectedRegion(null);
            session.setSelectedCity(null);
            session.setSelectedSubCity(null);
            session.setArea(null);
            session.setFormattedAddress("Exact location saved");

            userLocationService.saveLocation(
                    chatId,
                    lat,
                    lon,
                    null,
                    null,
                    null,
                    null,
                    "Exact location saved"
            );

            telegramClient.sendLocation(chatId, lat, lon);
            telegramClient.sendMessage(
                    chatId,
                    "✅ Exact location received.\n\n" +
                    "⚠️ This point does not match a nearby Ethiopia pharmacy area.\n\n" +
                    "Use one of these instead:\n" +
                    "• 🔗 Paste Google Maps Link\n" +
                    "• 🗺 Select Ethiopia Region"
            );

            telegramClient.sendLocationChoiceMenu(chatId);
        }

        return;
    }
}

        // 2) UPDATE PHARMACY LOCATION
        if (UpdateSessionManager.exists(chatId)) {
            UpdateSession updateSession = UpdateSessionManager.get(chatId);

            if (updateSession.getField() == UpdateField.LOCATION) {
                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                pharmacy.setLatitude(lat);
                pharmacy.setLongitude(lon);
                pharmacyRepository.save(pharmacy);

                UpdateSessionManager.remove(chatId);
                telegramClient.sendMessage(chatId, "✅ Pharmacy location updated successfully.");
                return;
            }
        }

        // 3) MULTI MEDICINE EXACT LOCATION
        if (MultiMedicineSearchSessionManager.exists(chatId)) {
            MultiMedicineSearchSession multiSession = MultiMedicineSearchSessionManager.get(chatId);

            if (multiSession.isWaitingForExactLocation()) {
                userLocationService.saveLocation(chatId, lat, lon);

                multiSession.setWaitingForExactLocation(false);
                multiSession.setWaitingForLocationChoice(false);
                multiSession.setWaitingForMedicineInput(true);

                telegramClient.sendMessage(
                        chatId,
                        "✅ Exact location saved for multi-medicine search.\n\nNow send the first medicine name."
                );
                telegramClient.sendMultiMedicineModeKeyboard(chatId);
                return;
            }
        }

        // 4) NORMAL USER LOCATION
        userLocationService.saveLocation(chatId, lat, lon);

        telegramClient.sendMessage(
                chatId,
                "📍 Location received.\n\nNow send medicine name."
        );
        restoreKeyboard(chatId);

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    }
}
  protected void handlePhotoMessage(TelegramUpdateDTO update, Long chatId) {
    var photos = update.getMessage().getPhoto();

    if (photos == null || photos.isEmpty()) {
        return;
    }

    String fileId = photos.get(photos.size() - 1).getFileId();
if (UpdateSessionManager.exists(chatId)) {
    UpdateSession session = UpdateSessionManager.get(chatId);

    if (session.getField() == UpdateField.PHOTO) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacy.setPhotoFileId(fileId);
            pharmacyRepository.save(pharmacy);

            telegramClient.sendMessage(chatId, "✅ Pharmacy photo updated successfully.");
            UpdateSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return;

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            restoreKeyboard(chatId);
            return;
        }
    }
}
    // ---------------- LICENSE UPDATE FOR EXISTING PHARMACY ----------------
    if (UpdateSessionManager.exists(chatId)) {
        UpdateSession session = UpdateSessionManager.get(chatId);

        if (session.getField() == UpdateField.LICENSE) {
            try {
                pharmacyService.savePendingLicenseUpdate(chatId, fileId);

                telegramClient.sendMessage(
                        chatId,
                        "📄 License received.\nWaiting admin approval."
                );

                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                String caption = "🔄 <b>License Update Request</b>\n\n"
                        + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                        + "🏙️ <b>City:</b> " + pharmacy.getCity() + "\n"
                        + "📍 <b>Area:</b> " + pharmacy.getArea() + "\n"
                        + "📞 <b>Phone:</b> " + pharmacy.getPhone() + "\n"
                        + "💊 <b>Medicines:</b> " + pharmacy.getMedicines() + "\n"
                        + "🕒 <b>Open:</b> " + pharmacy.getOpenTime() + "\n"
                        + "🌙 <b>Close:</b> " + pharmacy.getCloseTime() + "\n"
                        + "📌 <b>Latitude:</b> " + pharmacy.getLatitude() + "\n"
                        + "📌 <b>Longitude:</b> " + pharmacy.getLongitude() + "\n"
                        + "🆔 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                telegramClient.sendPhotoWithLicenseUpdateButtons(
                        ADMIN_CHAT_ID,
                        fileId,
                        caption,
                        chatId
                );

                UpdateSessionManager.remove(chatId);
                restoreKeyboard(chatId);
                return;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                restoreKeyboard(chatId);
                return;
            }
        }
    }

   // ---------------- NEW REGISTRATION LICENSE SUBMISSION ----------------
if (RegistrationSessionManager.exists(chatId)) {
    RegistrationSession session = RegistrationSessionManager.get(chatId);

    boolean hasBasicData =
            session.getName() != null && !session.getName().isBlank()
                    && session.getPhone() != null && !session.getPhone().isBlank()
                    && session.getMedicines() != null && !session.getMedicines().isBlank()
                    && session.getOpenTime() != null && !session.getOpenTime().isBlank()
                    && session.getCloseTime() != null && !session.getCloseTime().isBlank()
                    && session.getLatitude() != null
                    && session.getLongitude() != null;

    boolean registrationReady = hasBasicData;

    if (!registrationReady) {
        telegramClient.sendMessage(
                chatId,
                "⚠️ Registration is incomplete.\n\nPlease finish all steps before uploading the license."
        );
        return;
    }

    try {
        if (registrationService.exists(chatId)) {
            if (registrationService.licenseAlreadyUploaded(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ License already uploaded.");
                return;
            }
            registrationService.saveLocation(chatId, session.getLatitude(), session.getLongitude(),
                    session.getFormattedAddress(), session.getPlusCode(), session.getLandmark());
        } else {
            String city = (session.getCity() == null || session.getCity().isBlank())
        ? "Unknown City"
        : session.getCity();

String area = (session.getArea() == null || session.getArea().isBlank())
        ? "Unknown Area"
        : session.getArea();

            registrationService.register(
                    session.getName(),
                    city,
                    area,
                    session.getPhone(),
                    session.getMedicines(),
                    session.getOpenTime(),
                    session.getCloseTime(),
                    chatId
            );

            registrationService.saveLocation(chatId, session.getLatitude(), session.getLongitude(),
                    session.getFormattedAddress(), session.getPlusCode(), session.getLandmark());
        }

        registrationService.saveLocationDetails(
                chatId,
                session.getFormattedAddress(),
                session.getLandmark(),
                session.getPlusCode()
        );

        Long registrationId = registrationService.saveLicense(chatId, fileId);
        var reg = registrationService.getRegistration(registrationId);

        String caption = "🆕 <b>New Pharmacy Registration</b>\n\n"
                + "🏥 <b>Name:</b> " + reg.getName() + "\n"
                + "🏙️ <b>City:</b> " + reg.getCity() + "\n"
                + "📍 <b>Area:</b> " + reg.getArea() + "\n"
                + "📞 <b>Phone:</b> " + reg.getPhone() + "\n"
                + "💊 <b>Medicines:</b> " + reg.getMedicines() + "\n"
                + "🕒 <b>Open:</b> " + reg.getOpenTime() + "\n"
                + "🌙 <b>Close:</b> " + reg.getCloseTime() + "\n"
                + "📌 <b>Latitude:</b> " + reg.getLatitude() + "\n"
                + "📌 <b>Longitude:</b> " + reg.getLongitude() + "\n"
                + "🆔 <b>Telegram ID:</b> " + reg.getTelegramId();

        telegramClient.sendPhotoWithButtons(
                ADMIN_CHAT_ID,
                fileId,
                caption,
                registrationId
        );

        telegramClient.sendMessage(
                chatId,
                "📄 License received.\nWaiting admin approval."
        );

        RegistrationSessionManager.remove(chatId);
        MedicineSelectionSessionManager.remove(chatId);
        LocationSelectionSessionManager.remove(chatId);

        telegramClient.sendPendingPharmacyHome(chatId);
        return;

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        return;
    }
}
}

    protected void handleTextMessage(TelegramUpdateDTO update, Long chatId) {
    String text = update.getMessage().getText();

    if (text == null) {
        return;
    }

    String originalText = text.trim();
    String normalizedText = originalText.toLowerCase();
    if (handleRegistrationLocationText(chatId, text)) {
    return;
}

    // ---------------- SHARED LOCATION FLOW ----------------
    if (LocationSelectionSessionManager.exists(chatId)) {
        if (handleSharedLocationSelectionText(chatId, originalText)) {
            return;
        }
    }

    // ---------------- HOME ----------------
if (normalizedText.equals("/home")
        || normalizedText.equals("home")
        || normalizedText.equals(MAIN_HOME)
        || normalizedText.equals("🏠 home")) {
            clearOpenDetailExtras(chatId);
        if (RegistrationSessionManager.exists(chatId)) {
            RegistrationSessionManager.remove(chatId);
        }
        if (ReservationSessionManager.exists(chatId)) {
            ReservationSessionManager.remove(chatId);
        }
        if (UpdateSessionManager.exists(chatId)) {
            UpdateSessionManager.remove(chatId);
        }
        if (MedicineSelectionSessionManager.exists(chatId)) {
            MedicineSelectionSessionManager.remove(chatId);
        }
        if (LocationSelectionSessionManager.exists(chatId)) {
            LocationSelectionSessionManager.remove(chatId);
        }
        if (MultiMedicineSearchSessionManager.exists(chatId)) {
            MultiMedicineSearchSessionManager.remove(chatId);
        }
        if (MedicineSearchSessionManager.exists(chatId)) {
            MedicineSearchSessionManager.remove(chatId);
}

        if (chatId.equals(ADMIN_CHAT_ID)) {
            telegramClient.sendAdminDashboard(chatId);
            return;
        }

        if (pharmacyService.isRegisteredPharmacy(chatId)) {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
            telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
            return;
        }

        telegramClient.sendUserDashboard(chatId);
        return;
    }

    // ---------------- GLOBAL BACK FALLBACK ----------------
    if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
        if (!RegistrationSessionManager.exists(chatId)
                && !ReservationSessionManager.exists(chatId)
                && !UpdateSessionManager.exists(chatId)
                && !MultiMedicineSearchSessionManager.exists(chatId)
                && !LocationSelectionSessionManager.exists(chatId)) {
            restoreKeyboard(chatId);
            return;
        }
    }
    if (normalizedText.equals("🖼 update pharmacy photo")) {
    telegramClient.sendMessage(chatId, "🖼 Please upload your pharmacy photo or logo.");
    UpdateSessionManager.start(chatId, UpdateField.PHOTO);
    return;
}
    if (normalizedText.equals("🕘 recent searches")) {
    telegramClient.sendRecentSearches(chatId, medicineSearchLogService.getRecentSearches(chatId));
    return;
}

if (normalizedText.equals("🔔 my alerts")) {
    telegramClient.sendMyAlerts(
            chatId,
            medicineAvailabilityAlertService.getActiveAlerts(chatId)
    );
    return;
}
if (normalizedText.equals("❤️ favorite pharmacies")) {
    List<Pharmacy> favorites = favoritePharmacyService.getFavorites(chatId);

    if (favorites == null || favorites.isEmpty()) {
        telegramClient.sendMessage(chatId, "❤️ No favorite pharmacies yet.");
        return;
    }

    telegramClient.sendMessage(chatId, "❤️ <b>Your Favorite Pharmacies</b>", "HTML");

    for (Pharmacy pharmacy : favorites) {
        telegramClient.sendFavoritePharmacyCard(chatId, pharmacy);
    }
    return;
}
    // ---------------- RESUME REGISTRATION ----------------
    if (normalizedText.equals("🔁 resume registration")) {
        if (pharmacyService.isRegisteredPharmacy(chatId)) {
            telegramClient.sendMessage(chatId, "🏥 You already have a registered pharmacy.");
            return;
        }

        if (registrationService.exists(chatId)) {
            telegramClient.sendPendingPharmacyHome(chatId);
            return;
        }

        PharmacyRegistration rejected = registrationService.getLatestRejected(chatId);

        if (rejected == null) {
            telegramClient.sendMessage(chatId, "⚠️ No rejected registration found.");
            telegramClient.sendUserDashboard(chatId);
            return;
        }

        RegistrationSessionManager.remove(chatId);
        MedicineSelectionSessionManager.remove(chatId);

        RegistrationSessionManager.start(chatId);
        RegistrationSession session = RegistrationSessionManager.get(chatId);

        session.setName(rejected.getName());
        session.setCity(rejected.getCity());
        session.setArea(rejected.getArea());
        session.setPhone(rejected.getPhone());
        session.setMedicines(rejected.getMedicines());
        session.setOpenTime(rejected.getOpenTime());
        session.setCloseTime(rejected.getCloseTime());
        session.setLatitude(rejected.getLatitude());
        session.setLongitude(rejected.getLongitude());
        session.setSelectedCity(rejected.getCity());

        String rejectionReason = rejected.getRejectionReason() == null
                ? ""
                : rejected.getRejectionReason().toLowerCase();

        telegramClient.sendMessage(
                chatId,
                "🔁 Previous registration data loaded.\n\nReason: "
                        + (rejected.getRejectionReason() == null
                        ? "Please correct the issue."
                        : rejected.getRejectionReason())
        );

        if (rejectionReason.contains("location")
                || rejected.getLatitude() == null
                || rejected.getLongitude() == null) {
            session.setStep(RegistrationStep.LOCATION);
            session.clearLocationFlags();
            telegramClient.sendRegistrationLocationChoice(chatId);
            return;
        }

        if (rejectionReason.contains("license")
                || rejected.getLicenseFileId() == null
                || rejected.getLicenseFileId().isBlank()) {
            session.setStep(RegistrationStep.LOCATION);
            session.clearLocationFlags();
            telegramClient.sendRegistrationLocationChoice(chatId);
            telegramClient.sendMessage(chatId, "📄 After confirming location, please upload your pharmacy license.");
            return;
        }

        if (rejectionReason.contains("phone")) {
            session.setStep(RegistrationStep.PHONE);
            telegramClient.sendRegistrationPhonePrompt(chatId);
            return;
        }

        session.setStep(RegistrationStep.NAME);
        telegramClient.sendRegistrationNamePrompt(chatId);
        return;
    }

    // ---------------- START FRESH ----------------
    if (normalizedText.equals("🆕 start fresh")) {
        if (registrationService.exists(chatId)) {
            registrationService.deletePendingByTelegramId(chatId);
        }

        if (RegistrationSessionManager.exists(chatId)) {
            RegistrationSessionManager.remove(chatId);
        }

        if (MedicineSelectionSessionManager.exists(chatId)) {
            MedicineSelectionSessionManager.remove(chatId);
        }

        RegistrationSessionManager.start(chatId);
        telegramClient.sendRegistrationNamePrompt(chatId);
        return;
    }

    // ---------------- REGISTRATION FLOW ----------------
    if (RegistrationSessionManager.exists(chatId)) {
        RegistrationSession session = RegistrationSessionManager.get(chatId);

        if (normalizedText.equals(MAIN_HOME)) {
            RegistrationSessionManager.remove(chatId);

            if (MedicineSelectionSessionManager.exists(chatId)) {
                MedicineSelectionSessionManager.remove(chatId);
            }

            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (session.getStep() == RegistrationStep.LOCATION) {

if (normalizedText.contains("share exact pharmacy location")) {
        session.setExactLocationMode();
    telegramClient.sendRegistrationExactLocationHelp(chatId);
    return;
}

    if (normalizedText.contains("paste google maps link")) {
    session.setGoogleMapMode();
    telegramClient.sendRegistrationGoogleMapHelp(chatId);
    return;
}

    if (normalizedText.contains("🗺 select ethiopia region")) {
        session.clearLocationFlags();
        LocationSelectionSessionManager.start(chatId, LocationFlowType.REGISTRATION);
        telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
        return;
    }

    if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
        session.clearLocationFlags();
        session.setStep(RegistrationStep.MEDICINES);
        MedicineSelectionSessionManager.start(chatId, true);
        telegramClient.sendMedicinePicker(chatId, List.of());
        return;
    }

    if (session.isWaitingForGoogleMapLink()) {
        double[] coords = extractCoordinatesFromText(originalText);

        if (coords == null) {
            telegramClient.sendMessage(
                    chatId,
                    "⚠️ Invalid map link or coordinates.\n\nPlease paste a valid Google Maps link or lat,lon."
            );
            return;
        }

        double regLat = coords[0];
        double regLon = coords[1];

        session.setLatitude(regLat);
        session.setLongitude(regLon);
        session.clearLocationFlags();

        registrationService.saveLocation(chatId, regLat, regLon, null, null, null);

        telegramClient.sendLocation(chatId, regLat, regLon);
        telegramClient.sendMessage(
                chatId,
                "✅ Pharmacy location saved\n\n"
                        + "Step 7/7\n"
                        + "📄 Now upload your pharmacy license."
        );
        return;
    }
}

        if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
            switch (session.getStep()) {
                case CITY -> {
                    session.setStep(RegistrationStep.NAME);
                    telegramClient.sendRegistrationNamePrompt(chatId);
                    return;
                }
      case PHONE -> {
    session.setStep(RegistrationStep.NAME);
    telegramClient.sendRegistrationNamePrompt(chatId);
    return;
}
                case OPEN_HOUR, OPEN_MINUTE -> {
                    session.setStep(RegistrationStep.PHONE);
                    telegramClient.sendRegistrationPhonePrompt(chatId);
                    return;
                }
                case CLOSE_HOUR, CLOSE_MINUTE -> {
                    session.setStep(RegistrationStep.OPEN_HOUR);
                    telegramClient.sendHourPicker(chatId, "Step 3/7\n⏰ Select opening hour", "reg_open");
                    return;
                }
                case MEDICINES -> {
                    session.setStep(RegistrationStep.CLOSE_HOUR);
                    telegramClient.sendHourPicker(chatId, "Step 4/7\n🌙 Select closing hour", "reg_close");
                    return;
                }
                case LOCATION -> {
                    session.setStep(RegistrationStep.MEDICINES);
                    MedicineSelectionSessionManager.start(chatId, true);
                    telegramClient.sendMedicinePicker(chatId, List.of());
                    return;
                }
                default -> {
                    telegramClient.sendUserDashboard(chatId);
                    return;
                }
            }
        }

        switch (session.getStep()) {
     case NAME -> {
    session.setName(originalText);
    session.setStep(RegistrationStep.PHONE);
    telegramClient.sendRegistrationPhonePrompt(chatId);
    return;
}
case CITY, AREA -> {
    telegramClient.sendMessage(chatId, "⚠️ Please continue the registration flow.");
    return;
}

            case PHONE -> {
                String regPhone = originalText.replaceAll("\\s+", "");

                if (!regPhone.matches("^\\+?[0-9]{7,15}$")) {
                    telegramClient.sendRegistrationStepMessage(
                            chatId,
                            "⚠️ Invalid phone number.\n\nPlease enter digits only.\nExample:\n0912345678\n\nOr tap <b>Share Phone Number</b> below.",
                            RegistrationStep.PHONE
                    );
                    return;
                }

                session.setPhone(regPhone);
                session.setStep(RegistrationStep.OPEN_HOUR);

                telegramClient.sendHourPicker(
                        chatId,
                        "Step 3/7\n⏰ Select opening hour",
                        "reg_open"
                );
                return;
            }

            case OPEN_HOUR -> {
                telegramClient.sendHourPicker(chatId, "Step 3/7\n⏰ Select opening hour", "reg_open");
                return;
            }

            case OPEN_MINUTE -> {
                if (session.getTempHour() == null) {
                    telegramClient.sendHourPicker(chatId, "Step 3/7\n⏰ Select opening hour", "reg_open");
                } else {
                    telegramClient.sendMinutePicker(chatId, "Select opening minute", "reg_open", session.getTempHour());
                }
                return;
            }

            case CLOSE_HOUR -> {
                telegramClient.sendHourPicker(chatId, "Step 4/7\n🌙 Select closing hour", "reg_close");
                return;
            }

            case CLOSE_MINUTE -> {
                if (session.getTempHour() == null) {
                    telegramClient.sendHourPicker(chatId, "Step 4/7\n🌙 Select closing hour", "reg_close");
                } else {
                    telegramClient.sendMinutePicker(chatId, "Select closing minute", "reg_close", session.getTempHour());
                }
                return;
            }

            case MEDICINES -> {
                MedicineSelectionSessionManager.start(chatId, true);
                telegramClient.sendMedicinePicker(chatId, List.of());
                return;
            }

            case LOCATION -> {
                telegramClient.sendRegistrationLocationChoice(chatId);
                return;
            }
        }
    }

    // ---------------- ADMIN REJECT ----------------
    if (AdminRejectSessionManager.exists(chatId)) {
        AdminRejectSession rejectSession = AdminRejectSessionManager.get(chatId);
        String reason = originalText;

        try {
            if (rejectSession.getType() == AdminRejectType.REGISTRATION) {
                Long registrationId = rejectSession.getTargetId();
                PharmacyRegistration reg = registrationService.getRegistration(registrationId);

                registrationService.rejectWithReason(registrationId, reason);

                telegramClient.sendMessage(chatId, "❌ Registration rejected and reason sent.");
                telegramClient.sendRejectedRegistrationResumeMenu(reg.getTelegramId(), reason);
            } else if (rejectSession.getType() == AdminRejectType.LICENSE_UPDATE) {
                Long pharmacyTelegramId = rejectSession.getTargetId();

                pharmacyService.rejectPendingLicenseUpdate(pharmacyTelegramId);

                telegramClient.sendMessage(chatId, "❌ License update rejected and reason sent.");
                telegramClient.sendMessage(
                        pharmacyTelegramId,
                        "❌ Your license update was rejected.\n\n"
                                + "Reason: " + reason + "\n\n"
                                + "Please fix it and upload again."
                );
            }

            AdminRejectSessionManager.remove(chatId);
            return;

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            AdminRejectSessionManager.remove(chatId);
            return;
        }
    }

    // ---------------- CUSTOM MEDICINE INPUT ----------------
    if (MedicineSelectionSessionManager.exists(chatId)) {
        MedicineSelectionSession medSession = MedicineSelectionSessionManager.get(chatId);

        if (medSession.isWaitingCustomInput()) {
            String medicine = originalText.trim().toLowerCase();

            if (medicine.isBlank()) {
                telegramClient.sendMessage(chatId, "⚠️ Medicine name cannot be empty.");
                return;
            }

            List<String> suggestions = pharmacyService.suggestMedicines(medicine);

            if (suggestions != null && !suggestions.isEmpty()) {
                telegramClient.sendMedicineSuggestions(chatId, suggestions, medicine);
                return;
            }

            if (!medSession.getSelectedMedicines().contains(medicine)) {
                medSession.getSelectedMedicines().add(medicine);
            }

            medSession.setWaitingCustomInput(false);

            if (medSession.getPickerMessageId() != null) {
                telegramClient.editMedicinePicker(
                        chatId,
                        medSession.getPickerMessageId(),
                        medSession.getSelectedMedicines()
                );
            } else {
                telegramClient.sendMedicinePicker(chatId, medSession.getSelectedMedicines());
            }
            return;
        }
    }

    // ---------------- MULTI MEDICINE ----------------
    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);

        if (normalizedText.equals(MAIN_HOME)) {
            MultiMedicineSearchSessionManager.remove(chatId);
            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
            session.setWaitingForMedicineInput(true);
            telegramClient.sendMessage(chatId, "💊 Send another medicine name.");
            return;
        }

        if (normalizedText.equals("📍 use saved location")) {
            session.setWaitingForLocationChoice(false);
            session.setWaitingForMedicineInput(true);

            telegramClient.sendMultiMedicineModeKeyboard(chatId);
            telegramClient.sendMessage(chatId, "💊 Send the first medicine name.\n\nExample:\ninsulin");
            return;
        }

        if (normalizedText.equals("📍 share exact location")) {
            telegramClient.sendMultiMedicineChangeLocationMenu(chatId);
            telegramClient.sendMessage(chatId, "📍 Please share your exact location.");
            return;
        }

        if (normalizedText.equals("📌 share current location")) {
            session.setWaitingForLocationChoice(false);
            session.setWaitingForMedicineInput(true);
            session.setWaitingForExactLocation(true);

            telegramClient.sendMultiMedicineExactLocationRequest(chatId);
            return;
        }

        if (normalizedText.equals("🗺 select ethiopia region")) {
            LocationSelectionSessionManager.start(chatId, LocationFlowType.MULTI_MEDICINE);
            telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
            return;
        }

        if (normalizedText.equals("📍 change location")) {
            telegramClient.sendMultiMedicineChangeLocationMenu(chatId);
            return;
        }

        if (normalizedText.equals("➕ add more")) {
            session.setWaitingForMedicineInput(true);
            telegramClient.sendMessage(chatId, "💊 Send another medicine name.");
            return;
        }

        if (normalizedText.equals("🗑 clear")) {
            session.getSelectedMedicines().clear();
            session.setWaitingForMedicineInput(true);
            telegramClient.sendMessage(chatId, "🗑 Selected medicines cleared.\n\nNow send the first medicine name.");
            return;
        }

        if (normalizedText.equals("🔍 search pharmacies")) {
            if (session.getSelectedMedicines().isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ Add at least one medicine first.");
                return;
            }

            UserLocation loc = userLocationService.getLocation(chatId);
            if (loc == null) {
                telegramClient.sendLocationRequest(chatId);
                telegramClient.sendMessage(chatId, "📍 Please share your location first.");
                return;
            }

            var results = pharmacyService.searchMultipleMedicinesNearby(
                    session.getSelectedMedicines(),
                    loc.getLatitude(),
                    loc.getLongitude(),
                    chatId
            );

            if (results.isEmpty()) {
                telegramClient.sendMessage(chatId, "❌ No pharmacies found for the selected medicines.");
                return;
            }

            telegramClient.sendMessage(
                    chatId,
                    "🏥 <b>Multi-Medicine Search Results</b>\n\nShowing pharmacies with the best match first."
            );

            results.forEach(r -> telegramClient.sendMultiMedicinePharmacyResult(chatId, r));
            return;
        }

        if (session.isWaitingForMedicineInput()) {
            if (originalText.startsWith("/")) {
                MultiMedicineSearchSessionManager.remove(chatId);
                telegramClient.sendUserDashboard(chatId);
                return;
            }

            String medicine = originalText.trim().toLowerCase();

            if (medicine.length() < 2) {
                telegramClient.sendMessage(chatId, "⚠️ Medicine name is too short.");
                return;
            }

            if (session.getSelectedMedicines().size() >= 5) {
                telegramClient.sendMessage(chatId, "⚠️ Maximum 5 medicines allowed.");
                return;
            }

            if (!session.getSelectedMedicines().contains(medicine)) {
                session.getSelectedMedicines().add(medicine);
            }

            session.setWaitingForMedicineInput(false);

            telegramClient.sendMultiMedicineModeKeyboard(chatId);
            String selectedList = session.getSelectedMedicines().stream()
                    .map(m -> "• " + m)
                    .collect(Collectors.joining("\n"));

            telegramClient.sendMessage(
                    chatId,
                    "✅ Added: " + medicine + "\n\nCurrent medicines:\n" + selectedList
            );
            return;
        }
    }

    // ---------------- GENERAL MENU ----------------
    if (normalizedText.equals("🔎🛒 search multiple meds")) {
        boolean hasSavedLocation = userLocationService.getLocation(chatId) != null;
        MultiMedicineSearchSessionManager.start(chatId);
        telegramClient.sendMultiMedicineLocationKeyboard(chatId, hasSavedLocation);
        return;
    }

    if (normalizedText.equals("🔄 refresh")) {
        if (chatId.equals(ADMIN_CHAT_ID)) {
            telegramClient.sendAdminDashboard(chatId);
            return;
        }

        if (pharmacyService.isRegisteredPharmacy(chatId)) {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
            telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
            return;
        }

        if (registrationService.exists(chatId)) {
            telegramClient.sendPendingPharmacyHome(chatId);
            return;
        }

        telegramClient.sendUserDashboard(chatId);
        return;
    }
  if (MedicineSearchSessionManager.exists(chatId)) {

    if (normalizedText.equals("📍 nearest") || normalizedText.equals("📍 ✅ nearest")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.NEAREST, NEAREST);
        return;
    }

    if (normalizedText.equals("💰 cheapest") || normalizedText.equals("💰 ✅ cheapest")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.CHEAPEST, "Cheapest");
        return;
    }

    if (normalizedText.equals("⭐ highest rated") || normalizedText.equals("⭐ ✅ highest rated")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.HIGHEST_RATED, "Highest Rated");
        return;
    }

    if (normalizedText.equals("🟢 open now") || normalizedText.equals("🟢 ✅ open now")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.OPEN_NOW, "Open Now");
        return;
    }

    if (normalizedText.equals("📦 in stock only") || normalizedText.equals("📦 ✅ in stock only")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.IN_STOCK_ONLY, "In Stock Only");
        return;
    }

    if (normalizedText.equals("❌ clear filters")) {
        applyAndSendMedicineFilter(chatId, SearchFilterType.NEAREST, NEAREST);
        return;
    }

    if (normalizedText.equals(BACK_SYMBOL) || normalizedText.equals(BACK_ARROW)) {
            clearOpenDetailExtras(chatId);

        MedicineSearchSessionManager.remove(chatId);
        restoreKeyboard(chatId);
        return;
    }

    if (normalizedText.equals("🏠 home") || normalizedText.equals(MAIN_HOME)) {
            clearOpenDetailExtras(chatId);

        MedicineSearchSessionManager.remove(chatId);
        restoreKeyboard(chatId);
        return;
    }
}
    

    if (normalizedText.equals("🔎 search medicines") || normalizedText.equals("🔎 search medicine")) {
            clearOpenDetailExtras(chatId);

        telegramClient.sendMessage(chatId, "🔎 Send medicine name to search.");
        return;
    }

 if (normalizedText.equals("👤 account")) {
    telegramClient.sendMessage(chatId, buildUserAccountView(chatId));
    telegramClient.sendAccountMenu(chatId, pharmacyService.isRegisteredPharmacy(chatId));
    return;
}

    if (normalizedText.equals("❓ how to use")) {
        telegramClient.sendMessage(
                chatId,
                "❓ <b>How to Use TenaHub</b>\n\n" +
                        "1. Share your location\n" +
                        "2. Search for a medicine\n" +
                        "3. View nearby pharmacies\n" +
                        "4. Tap Reserve if available\n" +
                        "5. Enter quantity, name, and phone\n" +
                        "6. Wait for pharmacy approval\n" +
                        "7. Pick up before the hold time expires"
        );
        return;
    }

    if (normalizedText.equals("📝 leave feedback")) {
        telegramClient.sendMessage(
                chatId,
                "📝 Please type your feedback.\n\nWe will use it to improve TenaHub."
        );
        return;
    }

    if (normalizedText.equals("📖 information")) {
        telegramClient.sendMessage(
                chatId,
                "📖 <b>About TenaHub</b>\n\n" +
                        "TenaHub helps users find nearby pharmacies, check medicine availability, and reserve medicines before visiting.\n\n" +
                        "Pharmacy owners can manage inventory, reservations, and profile information through the bot."
        );
        return;
    }

    if (normalizedText.equals("🌐 language")) {
        telegramClient.sendMessage(
                chatId,
                "🌐 Language selection can be added next.\n\nCurrently the bot uses English."
        );
        return;
    }

    if (normalizedText.equals("/cancel") || normalizedText.equals("❌ cancel")) {
            clearOpenDetailExtras(chatId);

        if (RegistrationSessionManager.exists(chatId)) {
            RegistrationSessionManager.remove(chatId);

            if (MedicineSelectionSessionManager.exists(chatId)) {
                MedicineSelectionSessionManager.remove(chatId);
            }

            telegramClient.sendUserDashboard(chatId);
            return;
        }
        if (MedicineSearchSessionManager.exists(chatId)) {
    MedicineSearchSessionManager.remove(chatId);
}

        if (UpdateSessionManager.exists(chatId)) {
            UpdateSessionManager.remove(chatId);

            if (MedicineSelectionSessionManager.exists(chatId)) {
                MedicineSelectionSessionManager.remove(chatId);
            }

            restoreKeyboard(chatId);
            return;
        }

        if (MedicineSelectionSessionManager.exists(chatId)) {
            MedicineSelectionSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return;
        }

        if (ReservationSessionManager.exists(chatId)) {
            ReservationSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return;
        }
        

        if (registrationService.exists(chatId)) {
            telegramClient.sendMessage(
                    chatId,
                    "⏳ Your pharmacy registration is already submitted and waiting for admin approval."
            );
            telegramClient.sendPendingPharmacyHome(chatId);
            return;
        }

        telegramClient.sendUserDashboard(chatId);
        return;
    }

    if (normalizedText.equals("/start")) {
        if (chatId.equals(ADMIN_CHAT_ID)) {
            telegramClient.sendAdminDashboard(chatId);
            return;
        }

        if (pharmacyService.isRegisteredPharmacy(chatId)) {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
            return;
        }

        if (registrationService.exists(chatId)) {
            telegramClient.sendPendingPharmacyHome(chatId);
            return;
        }

        telegramClient.sendUserDashboard(chatId);
        return;
    }

    if (normalizedText.equals("📜 reservation history")) {
        telegramClient.sendMessage(chatId, reservationService.viewReservationHistory(chatId));
        return;
    }

    if (normalizedText.equals("🏥 register pharmacy")) {
        if (pharmacyService.isRegisteredPharmacy(chatId)) {
            telegramClient.sendMessage(
                    chatId,
                    "🏥 You already have a registered pharmacy.\n\nUse /update to update your profile."
            );
            return;
        }

        if (registrationService.exists(chatId)) {
            telegramClient.sendPendingPharmacyHome(chatId);
            return;
        }

        RegistrationSessionManager.start(chatId);
        telegramClient.sendRegistrationNamePrompt(chatId);
        return;
    }

    if (normalizedText.equals("📍 share location")) {
        telegramClient.sendLocationChoiceMenu(chatId);
        return;
    }

    if (normalizedText.equals("🗺 select ethiopia region")) {
        LocationSelectionSessionManager.start(chatId, LocationFlowType.USER_SEARCH);
        telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
        return;
    }

if (normalizedText.equals("📦 my reservations")) {
    List<MedicineReservation> reservations = reservationService.getUserReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, "📦 <b>My Reservations</b>\n\nNo reservations found.", "HTML");
        return;
    }

    telegramClient.sendMessage(chatId, "📦 <b>My Reservations</b>\n\nGrouped by latest status.", "HTML");

    sendReservationSection(chatId, reservations, MedicineReservationStatus.PENDING, "⏳ Pending");
    sendReservationSection(chatId, reservations, MedicineReservationStatus.APPROVED, "✅ Approved");
    sendReservationSection(chatId, reservations, MedicineReservationStatus.FULFILLED, "📦 Fulfilled");
    sendReservationSection(chatId, reservations, MedicineReservationStatus.CANCELLED, "❌ Cancelled");
    sendReservationSection(chatId, reservations, MedicineReservationStatus.EXPIRED, "⌛ Expired");
    sendReservationSection(chatId, reservations, MedicineReservationStatus.REJECTED, "🚫 Rejected");
    return;
}
    // ---------------- ADMIN MENU ----------------
    if (normalizedText.equals("🆕 pending registrations")) {
        if (AdminViewSessionManager.exists(chatId)) {
            AdminViewSession current = AdminViewSessionManager.get(chatId);

            if (current.getDetailMessageId() != null) {
                telegramClient.deleteMessage(chatId, current.getDetailMessageId());
            }

            AdminViewSessionManager.remove(chatId);
        }

        telegramClient.sendAdminPendingRegistrationsPage(
                chatId,
                adminService.getPendingRegistrationsPage(0, 10),
                0
        );
        return;
    }

    if (normalizedText.equals("📄 license updates")) {
        if (AdminViewSessionManager.exists(chatId)) {
            AdminViewSession current = AdminViewSessionManager.get(chatId);

            if (current.getDetailMessageId() != null) {
                telegramClient.deleteMessage(chatId, current.getDetailMessageId());
            }

            AdminViewSessionManager.remove(chatId);
        }

        telegramClient.sendAdminPendingLicenseUpdatesPage(
                chatId,
                adminService.getPendingLicenseUpdatesPage(0, 10),
                0
        );
        return;
    }

    if (normalizedText.equals("📦 reservation oversight")) {
        telegramClient.sendAdminReservationOversight(
                chatId,
                adminService.viewDetailedReservationOversight()
        );
        return;
    }

    if (normalizedText.equals("📊 system summary")) {
        telegramClient.sendAdminSystemSummary(
                chatId,
                adminService.viewDetailedSystemSummary()
        );
        return;
    }

    // ---------------- PHARMACY MENU ----------------
    if (normalizedText.equals("/update")) {
        if (!pharmacyService.isRegisteredPharmacy(chatId)) {
            telegramClient.sendMessage(chatId, "❌ Only registered pharmacies can update profile.");
            return;
        }

        telegramClient.sendUpdateMenu(chatId);
        return;
    }

    if (normalizedText.equals("⚙️ profile")) {
        telegramClient.sendUpdateMenu(chatId);
        return;
    }

    if (normalizedText.equals("📦 inventory")) {
        telegramClient.sendInventoryMenu(chatId);
        return;
    }

    if (normalizedText.equals("📦 reservations") || normalizedText.equals("📦 reservation management")) {
        telegramClient.sendReservationManagementMenu(chatId);
        return;
    }

if (normalizedText.equals("📦 pending reservations")) {
    List<MedicineReservation> reservations = reservationService.getPendingReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, "📦 <b>Pending Reservations</b>\n\nNo pending reservations.", "HTML");
        return;
    }

    telegramClient.sendMessage(chatId, "📦 <b>Pending Reservations</b>", "HTML");

    for (MedicineReservation r : reservations) {
        telegramClient.sendPharmacyPendingReservationCard(
                chatId,
                r.getId(),
                r.getUserId(),
                r.getMedicineName(),
                r.getRequestedQuantity(),
                r.getCustomerPhone(),
                r.getCustomerName()
        );
    }
    return;
}

if (normalizedText.equals("✅ approved reservations")) {
    List<MedicineReservation> reservations = reservationService.getApprovedReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, "✅ <b>Approved Reservations</b>\n\nNo approved reservations.", "HTML");
        return;
    }

    telegramClient.sendMessage(chatId, "✅ <b>Approved Reservations</b>", "HTML");

    java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");

    for (MedicineReservation r : reservations) {
        String holdUntil = r.getExpiresAt() == null ? null : formatter.format(r.getExpiresAt());

        telegramClient.sendPharmacyApprovedReservationCard(
                chatId,
                r.getId(),
                r.getUserId(),
                r.getMedicineName(),
                r.getRequestedQuantity(),
                r.getCustomerPhone(),
                r.getCustomerName(),
                holdUntil
        );
    }
    return;
}

   if (normalizedText.equals("📦 mark fulfilled")) {
    List<MedicineReservation> reservations = reservationService.getFulfillableReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, "📦 <b>Mark Fulfilled</b>\n\nNo fulfillable reservations.", "HTML");
        return;
    }

    telegramClient.sendMessage(chatId, "📦 <b>Mark Fulfilled</b>", "HTML");

    java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");

    for (MedicineReservation r : reservations) {
        String holdUntil = r.getExpiresAt() == null ? null : formatter.format(r.getExpiresAt());

        telegramClient.sendPharmacyApprovedReservationCard(
                chatId,
                r.getId(),
                r.getUserId(),
                r.getMedicineName(),
                r.getRequestedQuantity(),
                r.getCustomerPhone(),
                r.getCustomerName(),
                holdUntil
        );
    }
    return;
}

    if (normalizedText.equals("📦 view reservations")) {
        telegramClient.sendMessage(chatId, reservationService.viewPendingReservations(chatId));
        return;
    }

    if (normalizedText.equals("📞 update phone")) {
        telegramClient.sendMessage(chatId, "📞 Send the new phone number\nExample: 0912345678");
        UpdateSessionManager.start(chatId, UpdateField.PHONE);
        return;
    }

    if (normalizedText.equals("📄 update license")) {
        telegramClient.sendMessage(chatId, "📄 Upload the new license photo");
        UpdateSessionManager.start(chatId, UpdateField.LICENSE);
        return;
    }

    if (normalizedText.equals("⏰ update hours")) {
        UpdateSessionManager.start(chatId, UpdateField.HOURS);
        telegramClient.sendHourPicker(chatId, "⏰ Select opening hour", "update_open");
        return;
    }

    if (normalizedText.equals("💊 update medicines")) {
        UpdateSessionManager.start(chatId, UpdateField.MEDICINES);
        MedicineSelectionSessionManager.start(chatId, false);
        telegramClient.sendMedicinePicker(chatId, List.of());
        return;
    }
if (normalizedText.equals("📍 update location")) {
    UpdateSessionManager.start(chatId, UpdateField.LOCATION);
    LocationSelectionSessionManager.start(chatId, LocationFlowType.UPDATE_PHARMACY_LOCATION);
    telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
    return;
}

    if (normalizedText.equals("📦 update inventory")) {
        telegramClient.sendInventoryMenu(chatId);
        return;
    }

    if (normalizedText.equals("➕ add / update stock")) {
        telegramClient.sendMessage(
                chatId,
                "➕ Send stock like:\nmedicine quantity\n\nExample:\ninsulin 12"
        );
        UpdateSessionManager.start(chatId, UpdateField.INVENTORY_ADD);
        return;
    }
    if (normalizedText.equals("💰 update price")) {
    telegramClient.sendMessage(
            chatId,
            "💰 Send price like:\nmedicine | price\n\nExample:\ninsulin | 450"
    );
    UpdateSessionManager.start(chatId, UpdateField.PRICE);
    return;
}

    if (normalizedText.equals("📉 mark out of stock")) {
        telegramClient.sendMessage(
                chatId,
                "📉 Send medicine name to mark out of stock.\n\nExample:\ninsulin"
        );
        UpdateSessionManager.start(chatId, UpdateField.INVENTORY_OUT);
        return;
    }

    if (normalizedText.equals("📋 view inventory")) {
        try {
            List<PharmacyInventory> inventory = inventoryService.getInventory(chatId);
            telegramClient.sendMessage(chatId, buildInventoryView(inventory));
            return;
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            return;
        }
    }

    if (normalizedText.equals("📤 export inventory")) {
        try {
            byte[] csv = inventoryService.exportInventoryCsv(chatId);
            telegramClient.sendDocumentBytes(
                    chatId,
                    csv,
                    "inventory_export.csv",
                    "📤 Inventory export"
            );
            return;
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            return;
        }
    }

    if (normalizedText.equals("📊 inventory summary")) {
        telegramClient.sendSummaryMenu(chatId);
        return;
    }

    if (normalizedText.equals("📊 daily summary")) {
        telegramClient.sendMessage(chatId, inventoryService.buildSummary(chatId, "daily"));
        return;
    }

    if (normalizedText.equals("📊 weekly summary")) {
        telegramClient.sendMessage(chatId, inventoryService.buildSummary(chatId, "weekly"));
        return;
    }

    if (normalizedText.equals("📊 monthly summary")) {
        telegramClient.sendMessage(chatId, inventoryService.buildSummary(chatId, "monthly"));
        return;
    }

    if (normalizedText.equals("📊 yearly summary")) {
        telegramClient.sendMessage(chatId, inventoryService.buildSummary(chatId, "yearly"));
        return;
    }

    if (normalizedText.equals("⚠️ low stock alert")) {
        telegramClient.sendMessage(chatId, inventoryService.buildLowStockAlert(chatId));
        return;
    }

    if (normalizedText.equals("📈 demand insights")) {
        telegramClient.sendMessage(chatId, inventoryService.getDemandInsights(chatId));
        return;
    }

    if (normalizedText.equals("🎯 set low stock threshold")) {
        telegramClient.sendMessage(
                chatId,
                "🎯 Send threshold like:\nmedicine quantity\n\nExample:\ninsulin 5"
        );
        UpdateSessionManager.start(chatId, UpdateField.INVENTORY_THRESHOLD);
        return;
    }

    if (normalizedText.equals("📥 import inventory csv")) {
        telegramClient.sendMessage(
                chatId,
                "📥 Please upload CSV file with format:\nmedicine_name,quantity\ninsulin,20\nparacetamol,5"
        );
        return;
    }

    // ---------------- UPDATE SESSION ----------------
    if (UpdateSessionManager.exists(chatId)) {
        UpdateSession session = UpdateSessionManager.get(chatId);

        if (normalizedText.equals(MAIN_HOME)) {
            UpdateSessionManager.remove(chatId);

            if (MedicineSelectionSessionManager.exists(chatId)) {
                MedicineSelectionSessionManager.remove(chatId);
            }

            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
            UpdateSessionManager.remove(chatId);

            if (MedicineSelectionSessionManager.exists(chatId)) {
                MedicineSelectionSessionManager.remove(chatId);
            }

            if (pharmacyService.isRegisteredPharmacy(chatId)) {
                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
                telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
                return;
            }

            telegramClient.sendUserDashboard(chatId);
            return;
        }

        switch (session.getField()) {
            case PHONE -> {
                String phone = originalText.replaceAll("\\s+", "");

                if (!phone.matches("^\\+?[0-9]{7,15}$")) {
                    telegramClient.sendMessage(
                            chatId,
                            "⚠️ Invalid phone number.\n\nPlease enter digits only.\nExample:\n0912345678"
                    );
                    return;
                }

                pharmacyService.updatePhone(chatId, phone);
                telegramClient.sendMessage(chatId, "✅ Phone updated");
                UpdateSessionManager.remove(chatId);
                return;
            }

            case MEDICINES -> {
                MedicineSelectionSessionManager.start(chatId, false);
                telegramClient.sendMedicinePicker(chatId, List.of());
                return;
            }

            case INVENTORY_THRESHOLD -> {
                String[] thresholdParts = originalText.split("\\s+");
                if (thresholdParts.length < 2) {
                    telegramClient.sendMessage(chatId, "⚠️ Format: medicine threshold");
                    return;
                }

                String thresholdMedicine = thresholdParts[0];
                Integer thresholdQty;

                try {
                    thresholdQty = Integer.parseInt(thresholdParts[1]);
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Threshold must be a number.");
                    return;
                }

                inventoryService.setLowStockThreshold(chatId, thresholdMedicine, thresholdQty);
                telegramClient.sendMessage(chatId,
                        "✅ Low stock threshold set\n" + thresholdMedicine + " = " + thresholdQty);
                UpdateSessionManager.remove(chatId);
                return;
            }

            case HOURS -> {
                telegramClient.sendHourPicker(chatId, "⏰ Select opening hour", "update_open");
                return;
            }

            case LICENSE -> {
                telegramClient.sendMessage(chatId, "📄 Please upload the new license photo");
                return;
            }

            case LOCATION -> {
                telegramClient.sendMessage(chatId, "📍 Please send the new pharmacy location.");
                return;
            }

          case INVENTORY_ADD -> {
    String[] parts = originalText.split("\\s+");

    if (parts.length < 3) {
        telegramClient.sendMessage(
                chatId,
                "⚠️ Format: medicine quantity price\n\nExample:\ninsulin 12 120"
        );
        return;
    }

    String medicineName = parts[0];
    Integer quantity;
    java.math.BigDecimal price;

    try {
        quantity = Integer.parseInt(parts[1]);
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Quantity must be a number.");
        return;
    }

    try {
        price = new java.math.BigDecimal(parts[2]);
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Price must be a valid number.");
        return;
    }

    inventoryService.upsertStock(chatId, medicineName, quantity, price);

    telegramClient.sendMessage(
            chatId,
            "✅ Inventory updated\n" +
            "💊 " + medicineName + "\n" +
            "📦 Qty: " + quantity + "\n" +
            "💰 Price: " + price.stripTrailingZeros().toPlainString() + " ETB"
    );

    UpdateSessionManager.remove(chatId);
    telegramClient.sendInventoryMenu(chatId);
    return;
}

            case INVENTORY_OUT -> {
                inventoryService.markOutOfStock(chatId, originalText);
                telegramClient.sendMessage(chatId, "✅ Marked out of stock: " + originalText);

                UpdateSessionManager.remove(chatId);
                telegramClient.sendInventoryMenu(chatId);
                return;
            }
            case PRICE -> {
    String[] parts = originalText.split("\\|");

    if (parts.length < 2) {
        telegramClient.sendMessage(
                chatId,
                "⚠️ Format: medicine | price\n\nExample:\ninsulin | 450"
        );
        return;
    }

    String medicineName = parts[0].trim();

    java.math.BigDecimal price;
    try {
        price = new java.math.BigDecimal(parts[1].trim());
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Price must be a valid number.");
        return;
    }

    inventoryService.updatePrice(chatId, medicineName, price);

    telegramClient.sendMessage(
            chatId,
            "✅ Price updated\n\n" + medicineName + " = " + price + " ETB"
    );

    UpdateSessionManager.remove(chatId);
    telegramClient.sendInventoryMenu(chatId);
    return;
}
case PHOTO -> {
    telegramClient.sendMessage(chatId, "🖼 Please upload the pharmacy photo or logo.");
    return;
}
        }
    }

    // ---------------- RESERVATION FLOW ----------------
    if (ReservationSessionManager.exists(chatId)) {
        var session = ReservationSessionManager.get(chatId);

        if (normalizedText.equals(MAIN_HOME)) {
            ReservationSessionManager.remove(chatId);
            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
            if (session.isWaitingForPhone()) {
                session.setWaitingForPhone(false);
                session.setWaitingForName(true);

                telegramClient.sendMessage(
                        chatId,
                        "👤 Please enter your full name.\n\nExample:\nTeketsel Beyene"
                );
                return;
            }

            if (session.isWaitingForName()) {
                session.setWaitingForName(false);
                session.setQuantity(null);
                telegramClient.sendReservationQuantityPicker(chatId, session.getMedicineName());
                return;
            }

            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (session.getQuantity() == null && !session.isWaitingForName() && !session.isWaitingForPhone()) {
            int quantity;
            try {
                quantity = Integer.parseInt(originalText.trim());
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "⚠️ Quantity must be a number.");
                return;
            }

            if (quantity <= 0) {
                telegramClient.sendMessage(chatId, "⚠️ Quantity must be greater than 0.");
                return;
            }
session.setQuantity(quantity);
session.setWaitingForCustomQuantity(false);
session.setWaitingForName(true);

Integer sourceMessageId = session.getSourceMessageId();
if (sourceMessageId != null) {
    Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(session.getPharmacyId(), session.getMedicineName())
            .orElse(null);

    boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;

    UserLocation loc2 = userLocationService.getLocation(chatId);
    Double distance = null;
    if (loc2 != null && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        distance = com.tenahub.bot.util.GeoUtils.distance(
                loc2.getLatitude(),
                loc2.getLongitude(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude()
        );
    }

    telegramClient.editPharmacyMessageAskName(
            chatId,
            sourceMessageId,
            pharmacy.getName(),
            pharmacy.getArea(),
            pharmacy.getPhone(),
            distance,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getId(),
            pharmacy.getRating(),
            inventory == null ? 0 : inventory.getQuantity(),
            outOfStock,
            session.getMedicineName(),
            inventory == null ? null : inventory.getPrice(),
            isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
            quantity
    );
} else {
    telegramClient.sendMessage(
            chatId,
            "👤 Please send your full name for the reservation.\n\nExample:\nTeketsel Beyene"
    );
}
return;
        }

        if (session.isWaitingForName()) {
            String fullName = originalText.trim();

            if (fullName.isBlank() || fullName.length() < 3) {
                telegramClient.sendMessage(
                        chatId,
                        "⚠️ Please enter a valid full name.\n\nExample:\nTeketsel Beyene"
                );
                return;
            }

            session.setCustomerName(fullName);
            session.setWaitingForName(false);
            session.setWaitingForPhone(true);

            telegramClient.sendPhoneRequestKeyboard(
                    chatId,
                    "📱 Please share your phone number for the reservation.\n\nYou can tap the button below or type it manually."
            );
            return;
        }

        if (session.isWaitingForPhone()) {
            String phone = originalText.replaceAll("\\s+", "");

            if (!phone.matches("^\\+?[0-9]{7,15}$")) {
                telegramClient.sendMessage(
                        chatId,
                        "⚠️ Invalid phone number.\n\nPlease share using the button or type digits only.\nExample:\n0912345678"
                );
                return;
            }

            try {
                var reservation = reservationService.createReservation(
                        chatId,
                        session.getPharmacyId(),
                        session.getMedicineName(),
                        session.getQuantity(),
                        phone,
                        session.getCustomerName()
                );

                Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
                        if (pharmacy.getTelegramId() == null || pharmacy.getTelegramId() <= 0) {
    throw new RuntimeException("Pharmacy Telegram ID is missing");
                        }

                try {
                    telegramClient.sendReservationRequestToPharmacy(
                            pharmacy.getTelegramId(),
                            reservation.getId(),
                            chatId,
                            session.getMedicineName(),
                            session.getQuantity(),
                            phone,
                            session.getCustomerName()
                    );

                    telegramClient.sendMessage(
                            chatId,
                            "✅ Reservation request sent to pharmacy.\n\n"
                                    + MEDICINE_LABEL + session.getMedicineName() + "\n"
                                    + QUANTITY_LABEL + session.getQuantity() + "\n"
                                    + "👤 Name: " + session.getCustomerName() + "\n"
                                    + PHONE_LABEL + phone + "\n"
                                    + "🕒 Waiting for pharmacy approval."
                    );

                } catch (Exception notifyError) {
                    telegramClient.sendMessage(
                            chatId,
                            "✅ Reservation saved.\n\n"
                                    + MEDICINE_LABEL + session.getMedicineName() + "\n"
                                    + QUANTITY_LABEL + session.getQuantity() + "\n"
                                    + "👤 Name: " + session.getCustomerName() + "\n"
                                    + PHONE_LABEL + phone + "\n\n"
                                    + "⚠️ Could not notify the pharmacy automatically."
                    );
                }

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            } finally {
                ReservationSessionManager.remove(chatId);
                restoreKeyboard(chatId);
            }

            return;
        }
    }

    // ---------------- COMMANDS ----------------
    if (normalizedText.startsWith("/fulfill")) {
        if (!pharmacyService.isRegisteredPharmacy(chatId)) {
            telegramClient.sendMessage(chatId, "❌ Only registered pharmacies can fulfill reservations.");
            return;
        }

        String[] parts = originalText.split("\\s+");

        if (parts.length < 2) {
            telegramClient.sendMessage(
                    chatId,
                    "⚠️ Usage:\n/fulfill RESERVATION_ID\n\nExample:\n/fulfill 12"
            );
            return;
        }

        Long reservationId;
        try {
            reservationId = Long.parseLong(parts[1]);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, "⚠️ Reservation ID must be a number.");
            return;
        }

        try {
            var reservation = reservationService.fulfillReservation(reservationId);

            telegramClient.sendMessage(
                    chatId,
                    "✅ Reservation marked fulfilled.\n\n"
                            + "🆔 " + reservation.getId() + "\n"
                            + "💊 " + reservation.getMedicineName() + "\n"
                            + "🔢 Qty: " + reservation.getRequestedQuantity()
            );
            restoreKeyboard(reservation.getUserId());

            telegramClient.sendMessage(
                    reservation.getUserId(),
                    "📦 Your reservation has been fulfilled.\n\n"
                            + MEDICINE_LABEL + reservation.getMedicineName() + "\n"
                            + QUANTITY_LABEL + reservation.getRequestedQuantity()
            );
            restoreKeyboard(reservation.getUserId());

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }

        return;
    }

    if (normalizedText.startsWith("/reject")) {
        if (!pharmacyService.isRegisteredPharmacy(chatId)) {
            telegramClient.sendMessage(chatId, "❌ Only registered pharmacies can reject reservations.");
            return;
        }

        String[] parts = originalText.split("\\s+", 3);

        if (parts.length < 3) {
            telegramClient.sendMessage(
                    chatId,
                    "⚠️ Usage:\n/reject RESERVATION_ID reason\n\nExample:\n/reject 12 customer did not confirm"
            );
            return;
        }

        Long reservationId;
        try {
            reservationId = Long.parseLong(parts[1]);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, "⚠️ Reservation ID must be a number.");
            return;
        }

        String reason = parts[2];

        try {
            var reservation = reservationService.rejectReservation(reservationId, reason);

            telegramClient.sendMessage(
                    chatId,
                    "❌ Reservation rejected.\n\n"
                            + "🆔 " + reservation.getId() + "\n"
                            + "💊 " + reservation.getMedicineName() + "\n"
                            + "Reason: " + reservation.getRejectionReason()
            );
            restoreKeyboard(reservation.getUserId());

            telegramClient.sendMessage(
                    reservation.getUserId(),
                    "❌ Your reservation was rejected.\n\n"
                            + MEDICINE_LABEL + reservation.getMedicineName() + "\n"
                            + QUANTITY_LABEL + reservation.getRequestedQuantity() + "\n"
                            + "Reason: " + reservation.getRejectionReason()
            );
            restoreKeyboard(reservation.getUserId());

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }

        return;
    }

    // ---------------- NORMAL MEDICINE SEARCH ----------------
  // ---------------- NORMAL MEDICINE SEARCH ----------------
// ---------------- NORMAL MEDICINE SEARCH ----------------
String medicine = originalText.trim().toLowerCase();
UserLocation loc = userLocationService.getLocation(chatId);

if (loc == null) {
    telegramClient.sendLocationRequest(chatId);
    return;
}

double userLat = loc.getLatitude();
double userLon = loc.getLongitude();

List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
        medicine,
        userLat,
        userLon,
        chatId
);

MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
results = applySearchFilter(results, SearchFilterType.NEAREST);

if (!results.isEmpty()) {
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
    return;
}

boolean medicineExistsInCatalog = pharmacyService.medicineExistsInCatalog(medicine);

if (medicineExistsInCatalog) {
    telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
    return;
}

List<String> alternatives = pharmacyService.suggestAlternativeMedicines(medicine);

if (alternatives != null && !alternatives.isEmpty()) {
    telegramClient.sendAlternativeMedicineSuggestionsWithNotify(chatId, medicine, alternatives);
} else {
    telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
}
}







protected boolean handleSharedLocationSelectionText(Long chatId, String originalText) {
    if (!LocationSelectionSessionManager.exists(chatId)) {
        return false;
    }

    LocationSelectionSession session = LocationSelectionSessionManager.get(chatId);

    if (originalText == null) {
        return true;
    }

   if (originalText.equalsIgnoreCase("🏠 Main")) {
    LocationSelectionSessionManager.remove(chatId);

    if (RegistrationSessionManager.exists(chatId)) {
        RegistrationSessionManager.remove(chatId);
    }
    if (MedicineSelectionSessionManager.exists(chatId)) {
        MedicineSelectionSessionManager.remove(chatId);
    }
    if (UpdateSessionManager.exists(chatId)) {
        UpdateSessionManager.remove(chatId);
    }
    if (MultiMedicineSearchSessionManager.exists(chatId)
            && session.getFlowType() == LocationFlowType.MULTI_MEDICINE) {
        MultiMedicineSearchSessionManager.remove(chatId);
    }

    if (chatId.equals(ADMIN_CHAT_ID)) {
        telegramClient.sendAdminDashboard(chatId);
    } else if (pharmacyService.isRegisteredPharmacy(chatId)) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
        telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
    } else {
        telegramClient.sendUserDashboard(chatId);
    }
    return true;
}

    if (originalText.equalsIgnoreCase("❌ Cancel")) {
        LocationSelectionSessionManager.remove(chatId);

        if (session.getFlowType() == LocationFlowType.REGISTRATION) {
            RegistrationSession regSession = RegistrationSessionManager.get(chatId);
            if (regSession != null) {
                regSession.clearLocationFlags();
                regSession.setStep(RegistrationStep.MEDICINES);
                MedicineSelectionSessionManager.start(chatId, true);
                telegramClient.sendMedicinePicker(chatId, List.of());
                return true;
            }
        }

        restoreKeyboard(chatId);
        return true;
    }

    if (originalText.equalsIgnoreCase("⬅️ Back")) {
        handleSharedLocationBack(chatId, session);
        return true;
    }

    if (session.isWaitingRegion()) {
        String matchedRegion = EthiopiaLocationCatalog.getRegions().stream()
                .filter(r -> r.equalsIgnoreCase(originalText))
                .findFirst()
                .orElse(null);

        if (matchedRegion == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please select a region using the buttons.");
            return true;
        }

        session.setSelectedRegion(matchedRegion);

        if (EthiopiaLocationCatalog.isAddisAbabaRegion(matchedRegion)) {
            session.setCityMode();
            telegramClient.sendAddisAbabaCityKeyboard(chatId);
            return true;
        }

        session.setCityMode();
        telegramClient.sendCityKeyboard(
                chatId,
                matchedRegion,
                EthiopiaLocationCatalog.getCitiesByRegion(matchedRegion)
        );
        return true;
    }

    if (session.isWaitingCity()) {
        if (EthiopiaLocationCatalog.isAddisAbabaRegion(session.getSelectedRegion())) {
            if (!EthiopiaLocationCatalog.isAddisAbabaCity(originalText)) {
                telegramClient.sendMessage(chatId, "⚠️ Please choose Addis Ababa using the button.");
                return true;
            }

            session.setSelectedCity("Addis Ababa");
            session.setSubCityMode();
            telegramClient.sendAddisAbabaSubCityKeyboard(chatId);
            return true;
        }

        String matchedCity = EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion()).stream()
                .filter(c -> c.equalsIgnoreCase(originalText))
                .findFirst()
                .orElse(null);

        if (matchedCity == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please select a city using the buttons.");
            return true;
        }

        session.setSelectedCity(matchedCity);
        session.setAreaMode();

        telegramClient.sendAreaKeyboard(
                chatId,
                matchedCity,
                EthiopiaLocationCatalog.getAreasByRegionAndCity(session.getSelectedRegion(), matchedCity)
        );
        return true;
    }

    if (session.isWaitingSubCity()) {
        String matchedSubCity = EthiopiaLocationCatalog.getAddisAbabaSubCities().stream()
                .filter(s -> s.equalsIgnoreCase(originalText))
                .findFirst()
                .orElse(null);

        if (matchedSubCity == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please select a sub-city using the buttons.");
            return true;
        }

        session.setSelectedSubCity(matchedSubCity);
        session.setAreaMode();

        telegramClient.sendAddisAbabaAreaBySubCityKeyboard(
                chatId,
                matchedSubCity,
                EthiopiaLocationCatalog.getAddisAreasBySubCity(matchedSubCity)
        );
        return true;
    }

    if (session.isWaitingArea()) {
        String matchedArea;

        if (EthiopiaLocationCatalog.isAddisAbabaRegion(session.getSelectedRegion())) {
            matchedArea = EthiopiaLocationCatalog.getAddisAreasBySubCity(session.getSelectedSubCity()).stream()
                    .filter(a -> a.equalsIgnoreCase(originalText))
                    .findFirst()
                    .orElse(null);
        } else {
            matchedArea = EthiopiaLocationCatalog.getAreasByRegionAndCity(
                    session.getSelectedRegion(),
                    session.getSelectedCity()
            ).stream()
                    .filter(a -> a.equalsIgnoreCase(originalText))
                    .findFirst()
                    .orElse(null);
        }

        if (matchedArea == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please select an area using the buttons.");
            return true;
        }

        session.setSelectedArea(matchedArea);
        completeSharedLocationSelection(chatId, session);
        return true;
    }

    return true;
}

protected void handleSharedLocationBack(Long chatId, LocationSelectionSession session) {
    if (session.isWaitingArea()) {
        if (EthiopiaLocationCatalog.isAddisAbabaRegion(session.getSelectedRegion())
                && session.getSelectedSubCity() != null) {
            session.setSubCityMode();
            telegramClient.sendAddisAbabaSubCityKeyboard(chatId);
            return;
        }

        session.setCityMode();
        telegramClient.sendCityKeyboard(
                chatId,
                session.getSelectedRegion(),
                EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion())
        );
        return;
    }

    if (session.isWaitingSubCity()) {
        session.setCityMode();
        session.setSelectedSubCity(null);
        telegramClient.sendAddisAbabaCityKeyboard(chatId);
        return;
    }

    if (session.isWaitingCity()) {
        session.setRegionMode();
        session.setSelectedCity(null);
        session.setSelectedSubCity(null);
        telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
        return;
    }

    if (session.isWaitingRegion()) {
        LocationSelectionSessionManager.remove(chatId);

        switch (session.getFlowType()) {
            case REGISTRATION -> {
                RegistrationSession regSession = RegistrationSessionManager.get(chatId);
                if (regSession != null) {
                    regSession.setStep(RegistrationStep.LOCATION);
                    regSession.clearLocationFlags();
                    telegramClient.sendRegistrationLocationChoice(chatId);
                } else {
                    restoreKeyboard(chatId);
                }
            }
            case MULTI_MEDICINE -> {
                boolean hasSavedLocation = userLocationService.getLocation(chatId) != null;
                telegramClient.sendMultiMedicineLocationKeyboard(chatId, hasSavedLocation);
            }
            case USER_SEARCH -> {
                telegramClient.sendLocationChoiceMenu(chatId);
            }
            case UPDATE_PHARMACY_LOCATION -> {
                telegramClient.sendMessage(chatId, "📍 Please choose the new pharmacy location.");
                telegramClient.sendLocationChoiceMenu(chatId);
            }
        }
    }
}
protected void completeSharedLocationSelection(Long chatId, LocationSelectionSession session) {
    EthiopiaLocationOption option;

    if (EthiopiaLocationCatalog.isAddisAbabaRegion(session.getSelectedRegion())) {
        option = EthiopiaLocationCatalog.find(
                "Addis Ababa",
                "Addis Ababa",
                session.getSelectedArea()
        );
    } else {
        option = EthiopiaLocationCatalog.find(
                session.getSelectedRegion(),
                session.getSelectedCity(),
                session.getSelectedArea()
        );
    }

    if (option == null) {
        telegramClient.sendMessage(chatId, "⚠️ Could not resolve selected location.");
        return;
    }

    switch (session.getFlowType()) {
        case USER_SEARCH -> {
    String displayName = buildDisplayLocationName(
            option.getRegion(),
            option.getCity(),
            session.getSelectedSubCity(),
            option.getArea()
    );

    userLocationService.saveLocation(
            chatId,
            option.getLatitude(),
            option.getLongitude(),
            option.getRegion(),
            option.getCity(),
            session.getSelectedSubCity(),
            option.getArea(),
            displayName
    );

    telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());

    telegramClient.sendMessage(
            chatId,
            "✅ Location saved\n\n" +
                    "Region: " + option.getRegion() + "\n" +
                    "City: " + option.getCity() + "\n" +
                    (session.getSelectedSubCity() != null
                            ? "Sub-city: " + session.getSelectedSubCity() + "\n"
                            : "") +
                    "Area: " + option.getArea() + "\n\n" +
                    "Now send medicine name."
    );
}
           case MULTI_MEDICINE -> {
    String displayName = buildDisplayLocationName(
            option.getRegion(),
            option.getCity(),
            session.getSelectedSubCity(),
            option.getArea()
    );

    userLocationService.saveLocation(
            chatId,
            option.getLatitude(),
            option.getLongitude(),
            option.getRegion(),
            option.getCity(),
            session.getSelectedSubCity(),
            option.getArea(),
            displayName
    );

    telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());

    MultiMedicineSearchSession multiSession = MultiMedicineSearchSessionManager.get(chatId);
    if (multiSession != null) {
        multiSession.setWaitingForMedicineInput(true);
    }

    telegramClient.sendMessage(
            chatId,
            "✅ Location saved\n\n" +
                    "Region: " + option.getRegion() + "\n" +
                    "City: " + option.getCity() + "\n" +
                    (session.getSelectedSubCity() != null
                            ? "Sub-city: " + session.getSelectedSubCity() + "\n"
                            : "") +
                    "Area: " + option.getArea() + "\n\n" +
                    "Now send the first medicine name."
    );

    telegramClient.sendMultiMedicineModeKeyboard(chatId);
}

     case REGISTRATION -> {
    RegistrationSession regSession = RegistrationSessionManager.get(chatId);
    if (regSession == null) {
        telegramClient.sendMessage(chatId, "⚠️ Registration session not found.");
        return;
    }

    regSession.setSelectedRegion(session.getSelectedRegion());
    regSession.setCity(option.getCity());
    regSession.setSelectedCity(option.getCity());
    regSession.setSelectedSubCity(session.getSelectedSubCity());
    regSession.setArea(option.getArea());
    regSession.setLatitude(option.getLatitude());
    regSession.setLongitude(option.getLongitude());
    regSession.clearLocationFlags();

    telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());
    telegramClient.sendMessage(
            chatId,
            "✅ Pharmacy location saved\n\n" +
                    "Region: " + option.getRegion() + "\n" +
                    "City: " + option.getCity() + "\n" +
                    (session.getSelectedSubCity() != null ? "Sub-city: " + session.getSelectedSubCity() + "\n" : "") +
                    "Area: " + option.getArea() + "\n\n" +
                    "📄 Now upload your pharmacy license."
    );
}

        case UPDATE_PHARMACY_LOCATION -> {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacy.setLatitude(option.getLatitude());
            pharmacy.setLongitude(option.getLongitude());
            pharmacy.setCity(option.getCity());
            pharmacy.setArea(option.getArea());
            pharmacyRepository.save(pharmacy);

            telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());
            telegramClient.sendMessage(chatId, "✅ Pharmacy location updated successfully.");
        }
    }

    LocationSelectionSessionManager.remove(chatId);
}

    protected String buildInventoryView(List<PharmacyInventory> inventory) {

    if (inventory == null || inventory.isEmpty()) {
        return "📦 <b>Your Inventory</b>\n\nInventory is empty.";
    }

    StringBuilder inStock = new StringBuilder();
    StringBuilder lowStock = new StringBuilder();
    StringBuilder outOfStock = new StringBuilder();

    for (PharmacyInventory item : inventory) {
        String medicine = item.getMedicineName();
        Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();

        String priceText = item.getPrice() != null
                ? item.getPrice().toPlainString() + " " +
                  (item.getCurrency() == null || item.getCurrency().isBlank() ? "ETB" : item.getCurrency())
                : "No price";

        if (item.isOutOfStock() || qty <= 0) {
            outOfStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(priceText)
                    .append("\n");
        } else if (qty <= 10) {
            lowStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(qty).append(" left")
                    .append(" — ")
                    .append(priceText)
                    .append("\n");
        } else {
            inStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(qty).append(" left")
                    .append(" — ")
                    .append(priceText)
                    .append("\n");
        }
    }

    StringBuilder result = new StringBuilder("📋 <b>Your Inventory</b>\n\n");

    if (!inStock.isEmpty()) {
        result.append("✅ <b>In Stock</b>\n").append(inStock).append("\n");
    }

    if (!lowStock.isEmpty()) {
        result.append("⚠️ <b>Low Stock</b>\n").append(lowStock).append("\n");
    }

    if (!outOfStock.isEmpty()) {
        result.append("❌ <b>Out of Stock</b>\n").append(outOfStock).append("\n");
    }

    return result.toString().trim();
}
protected String buildUserAccountView(Long chatId) {
    StringBuilder sb = new StringBuilder();

    UserLocation location = userLocationService.getLocation(chatId);
    boolean isRegisteredPharmacy = pharmacyService.isRegisteredPharmacy(chatId);

    sb.append("👤 <b>Account Overview</b>\n\n");
    sb.append("🆔 <b>Telegram ID:</b> ").append(chatId).append("\n");

    if (location != null) {
        String locationLabel;

        if (location.getDisplayName() != null && !location.getDisplayName().isBlank()) {
            locationLabel = location.getDisplayName();
        } else if (location.getArea() != null && !location.getArea().isBlank()
                && location.getCity() != null && !location.getCity().isBlank()) {
            locationLabel = location.getArea() + ", " + location.getCity();
        } else if (location.getCity() != null && !location.getCity().isBlank()) {
            locationLabel = location.getCity();
        } else if (location.getLatitude() != null && location.getLongitude() != null) {
            locationLabel = location.getLatitude() + ", " + location.getLongitude();
        } else {
            locationLabel = "Saved ✅";
        }

        sb.append("📍 <b>Saved Location:</b> ").append(locationLabel).append("\n");
    } else {
        sb.append("📍 <b>Saved Location:</b> Not saved\n");
    }

    List<MedicineReservation> allReservations = reservationService.getUserReservations(chatId);

    long pendingCount = allReservations.stream()
            .filter(r -> r.getStatus() == MedicineReservationStatus.PENDING)
            .count();

    long approvedCount = allReservations.stream()
            .filter(r -> r.getStatus() == MedicineReservationStatus.APPROVED)
            .count();

    long fulfilledCount = allReservations.stream()
            .filter(r -> r.getStatus() == MedicineReservationStatus.FULFILLED)
            .count();

    long cancelledCount = allReservations.stream()
            .filter(r -> r.getStatus() == MedicineReservationStatus.CANCELLED)
            .count();

    long expiredCount = allReservations.stream()
            .filter(r -> r.getStatus() == MedicineReservationStatus.EXPIRED)
            .count();

    sb.append("\n📦 <b>Reservations</b>\n")
      .append("• Pending: ").append(pendingCount).append("\n")
      .append("• Approved: ").append(approvedCount).append("\n")
      .append("• Fulfilled: ").append(fulfilledCount).append("\n")
      .append("• Cancelled: ").append(cancelledCount).append("\n")
      .append("• Expired: ").append(expiredCount).append("\n");

    sb.append("\n🏥 <b>Pharmacy Status:</b> ")
      .append(isRegisteredPharmacy ? "Registered ✅" : "Not Registered")
      .append("\n");

    if (!isRegisteredPharmacy) {
        sb.append("💡 You can register your pharmacy from the menu.\n");
    }

    sb.append("\n🕘 <b>Recent Reservations</b>\n");

    if (allReservations.isEmpty()) {
        sb.append("• No reservations yet\n");
    } else {
        allReservations.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(3)
                .forEach(r -> {
                    String pharmacyName = pharmacyRepository.findById(r.getPharmacyId())
                            .map(Pharmacy::getName)
                            .orElse("Unknown Pharmacy");

                    sb.append("• ")
                      .append(r.getMedicineName())
                      .append(" × ")
                      .append(r.getRequestedQuantity())
                      .append(" — ")
                      .append(shorten(pharmacyName, 22))
                      .append(" [")
                      .append(r.getStatus())
                      .append("]\n");
                });
    }

    sb.append("\nUse the buttons below for quick actions.");

    return sb.toString();
}

protected String shorten(String text, int max) {
    if (text == null) return "Unknown";
    if (text.length() <= max) return text;
    return text.substring(0, max - 3) + "...";
}
   protected void restoreKeyboard(Long chatId) {
    if (chatId.equals(ADMIN_CHAT_ID)) {
        telegramClient.sendAdminDashboard(chatId);
        return;
    }
    

    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        telegramClient.sendMultiMedicineModeKeyboard(chatId);
        return;
    }

    if (LocationSelectionSessionManager.exists(chatId)) {
    LocationSelectionSession locationSession = LocationSelectionSessionManager.get(chatId);

    if (locationSession.isWaitingRegion()) {
        telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
        return;
    }

    if (locationSession.isWaitingCity()) {
        if (EthiopiaLocationCatalog.isAddisAbabaRegion(locationSession.getSelectedRegion())) {
            telegramClient.sendAddisAbabaCityKeyboard(chatId);
        } else {
            telegramClient.sendCityKeyboard(
                    chatId,
                    locationSession.getSelectedRegion(),
                    EthiopiaLocationCatalog.getCitiesByRegion(locationSession.getSelectedRegion())
            );
        }
        return;
    }

    if (locationSession.isWaitingSubCity()) {
        telegramClient.sendAddisAbabaSubCityKeyboard(chatId);
        return;
    }

    if (locationSession.isWaitingArea()) {
        if (EthiopiaLocationCatalog.isAddisAbabaRegion(locationSession.getSelectedRegion())) {
            telegramClient.sendAddisAbabaAreaBySubCityKeyboard(
                    chatId,
                    locationSession.getSelectedSubCity(),
                    EthiopiaLocationCatalog.getAddisAreasBySubCity(locationSession.getSelectedSubCity())
            );
        } else {
            telegramClient.sendAreaKeyboard(
                    chatId,
                    locationSession.getSelectedCity(),
                    EthiopiaLocationCatalog.getAreasByRegionAndCity(
                            locationSession.getSelectedRegion(),
                            locationSession.getSelectedCity()
                    )
            );
        }
        return;
    }
}

    if (pharmacyService.isRegisteredPharmacy(chatId)) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
        telegramClient.sendPharmacyDashboard(chatId, pharmacy.getName());
        return;
    }

    telegramClient.sendUserDashboard(chatId);
}

  
    protected double[] extractCoordinatesFromText(String text) {
    if (text == null || text.isBlank()) {
        return null;
    }

    String input = text.trim();

    try {
        if (input.matches("^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$")) {
            String[] parts = input.split(",");
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            return new double[]{lat, lon};
        }

        java.util.regex.Matcher qMatcher =
                java.util.regex.Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
                        .matcher(input);
        if (qMatcher.find()) {
            double lat = Double.parseDouble(qMatcher.group(1));
            double lon = Double.parseDouble(qMatcher.group(2));
            return new double[]{lat, lon};
        }

        java.util.regex.Matcher atMatcher =
                java.util.regex.Pattern.compile("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
                        .matcher(input);
        if (atMatcher.find()) {
            double lat = Double.parseDouble(atMatcher.group(1));
            double lon = Double.parseDouble(atMatcher.group(2));
            return new double[]{lat, lon};
        }

    } catch (Exception ignored) {
    }

    return null;
}

protected String buildPharmacyAddress(Pharmacy pharmacy) {
    if (pharmacy == null) {
        return "N/A";
    }

    boolean exactLocation =
            "Exact Location".equalsIgnoreCase(pharmacy.getCity()) ||
            "Exact Location".equalsIgnoreCase(pharmacy.getArea());

    if (exactLocation && pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        return "Lat: " + pharmacy.getLatitude() + ", Lon: " + pharmacy.getLongitude();
    }

    String city = pharmacy.getCity() == null ? "" : pharmacy.getCity().trim();
    String area = pharmacy.getArea() == null ? "" : pharmacy.getArea().trim();

    String address = (city + (city.isBlank() || area.isBlank() ? "" : ", ") + area).trim();

    return address.isBlank() ? "N/A" : address;
}


protected EthiopiaLocationOption resolveNearestCatalogLocation(double lat, double lon) {
    EthiopiaLocationOption nearest = EthiopiaLocationCatalog.findNearest(lat, lon);

    if (nearest == null) {
        return null;
    }

    double distanceKm = EthiopiaLocationCatalog.distanceKm(
            lat, lon,
            nearest.getLatitude(), nearest.getLongitude()
    );

    // you can tune this: 5km, 8km, 10km...
    if (distanceKm <= 10.0) {
        return nearest;
    }

    return null;
}
protected String buildDisplayLocationName(String region, String city, String subCity, String area) {
    if (subCity != null && !subCity.isBlank() && area != null && !area.isBlank() && city != null && !city.isBlank()) {
        return area + ", " + subCity + ", " + city;
    }

    if (area != null && !area.isBlank() && city != null && !city.isBlank()) {
        return area + ", " + city;
    }

    if (city != null && !city.isBlank()) {
        return city;
    }

    if (region != null && !region.isBlank()) {
        return region;
    }

    return "Exact location saved";
}
protected List<PharmacyResponseDTO> applySearchFilter(List<PharmacyResponseDTO> results, SearchFilterType filterType) {
    if (results == null || results.isEmpty()) {
        return List.of();
    }

    return switch (filterType) {
        case NEAREST -> results.stream()
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .toList();

        case CHEAPEST -> results.stream()
                .sorted((a, b) -> {
                    BigDecimal pa = a.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : a.getPrice();
                    BigDecimal pb = b.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : b.getPrice();
                    return pa.compareTo(pb);
                })
                .toList();

        case HIGHEST_RATED -> results.stream()
                .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
                .toList();

        case OPEN_NOW -> results.stream()
                .filter(PharmacyResponseDTO::isOpenNow)
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .toList();

        case IN_STOCK_ONLY -> results.stream()
                .filter(p -> !p.isOutOfStock()
                        && p.getStockQuantity() != null
                        && p.getStockQuantity() > 0)
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .toList();

        case CLEAR -> results.stream()
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .toList();
    };
}
protected void applyAndSendMedicineFilter(Long chatId, SearchFilterType filterType, String activeLabel) {
    if (!MedicineSearchSessionManager.exists(chatId)) {
        telegramClient.sendMessage(chatId, "⚠️ No recent medicine search found.");
        restoreKeyboard(chatId);
        return;
    }

    MedicineSearchSession session = MedicineSearchSessionManager.get(chatId);
    String medicine = session.getMedicineName();

    UserLocation loc = userLocationService.getLocation(chatId);
    if (loc == null) {
        telegramClient.sendMessage(chatId, "⚠️ Please share your location first.");
        restoreKeyboard(chatId);
        return;
    }

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    results = applySearchFilter(results, filterType);

    MedicineSearchSessionManager.save(chatId, medicine, filterType);

    if (results.isEmpty()) {
        telegramClient.sendMessage(
                chatId,
                "❌ No pharmacies found after applying filter: " + activeLabel
        );
        telegramClient.sendSearchFilterKeyboard(chatId, activeLabel);
        return;
    }

    telegramClient.sendMessage(
            chatId,
            "💊 <b>Filtered results for " + medicine + "</b>\n\n" +
            "Active filter: <b>" + activeLabel + "</b>"
    );

sendPharmacyResultsWithTopMap(chatId, results);
    telegramClient.sendSearchFilterKeyboard(chatId, activeLabel);
}
protected boolean isOpenNow(java.time.LocalTime open, java.time.LocalTime close) {
    if (open == null || close == null) {
        return false;
    }

    java.time.LocalTime now = java.time.LocalTime.now();

    if (close.equals(open)) {
        return true; // assume 24 hours
    }

    if (close.isAfter(open)) {
        return !now.isBefore(open) && !now.isAfter(close);
    }

    // overnight hours like 22:00 -> 06:00
    return !now.isBefore(open) || !now.isAfter(close);
}


protected void sendReservationSection(Long chatId,
                                    List<MedicineReservation> reservations,
                                    MedicineReservationStatus status,
                                    String title) {
    List<MedicineReservation> filtered = reservations.stream()
            .filter(r -> r.getStatus() == status)
            .toList();

    if (filtered.isEmpty()) {
        return;
    }

    telegramClient.sendMessage(chatId, "<b>" + title + "</b>", "HTML");

for (MedicineReservation r : filtered) {
    Pharmacy pharmacy = pharmacyRepository.findById(r.getPharmacyId()).orElse(null);

    String pharmacyName = pharmacy != null && pharmacy.getName() != null
            ? pharmacy.getName()
            : "Unknown Pharmacy";

    String pharmacyAddress = buildPharmacyAddress(pharmacy);

    boolean canCancel =
            r.getStatus() == MedicineReservationStatus.PENDING
                    || r.getStatus() == MedicineReservationStatus.APPROVED;

    String holdUntil = null;
    if (r.getStatus() == MedicineReservationStatus.APPROVED && r.getExpiresAt() != null) {
        holdUntil = r.getExpiresAt().format(
                java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
        );
    }

    if (canCancel) {
        telegramClient.sendUserReservationItemWithCancel(
                chatId,
                r.getId(),
                pharmacyName,
                pharmacyAddress,
                r.getMedicineName(),
                r.getRequestedQuantity(),
                r.getStatus().name(),
                holdUntil
        );
    } else {
        telegramClient.sendUserReservationItemReadOnly(
                chatId,
                r.getId(),
                pharmacyName,
                pharmacyAddress,
                r.getMedicineName(),
                r.getRequestedQuantity(),
                r.getStatus().name(),
                holdUntil
        );
    }
}
}

protected String buildFullAddress(Pharmacy pharmacy) {
    if (pharmacy == null) return "N/A";

    String city = pharmacy.getCity() == null ? "" : pharmacy.getCity().trim();
    String area = pharmacy.getArea() == null ? "" : pharmacy.getArea().trim();

    if (!city.isBlank() && !area.isBlank()) {
        return city + ", " + area;
    }
    if (!city.isBlank()) {
        return city;
    }
    if (!area.isBlank()) {
        return area;
    }

    if (pharmacy.getLatitude() != null && pharmacy.getLongitude() != null) {
        return pharmacy.getLatitude() + ", " + pharmacy.getLongitude();
    }

    return "Pharmacy location";
}
protected void clearOpenDetailExtras(Long chatId) {
    if (!PharmacyDetailViewSessionManager.exists(chatId)) {
        return;
    }

    PharmacyDetailViewSession session = PharmacyDetailViewSessionManager.get(chatId);

    if (session.getPhotoMessageId() != null) {
        telegramClient.deleteMessage(chatId, session.getPhotoMessageId());
    }

    PharmacyDetailViewSessionManager.remove(chatId);
}

protected void sendPharmacyResultsWithTopMap(Long chatId, List<PharmacyResponseDTO> results) {
    if (results == null || results.isEmpty()) {
        return;
    }

    for (PharmacyResponseDTO p : results) {
        telegramClient.sendPharmacyVenue(
                chatId,
                p.getName(),
                p.getArea() == null ? "Pharmacy location" : p.getArea(),
                p.getLatitude(),
                p.getLongitude()
        );

        telegramClient.sendPharmacyResult(
                chatId,
                p.getName(),
                p.getArea(),
                p.getPhone(),
                p.getDistance(),
                p.getLatitude(),
                p.getLongitude(),
                p.getId(),
                p.getRating(),
                p.isCanRate(),
                p.isFavourite(),
                p.getStockQuantity(),
                p.isOutOfStock(),
                p.getMedicineName(),
                p.getPrice(),
                p.isOpenNow(),
                p.getOpenTime(),
                p.getCloseTime()
        );
    }
}

protected void resendTrackedSearchFilter(Long chatId, String activeFilter) {
    if (SearchFilterViewSessionManager.exists(chatId)) {
        SearchFilterViewSession oldFilter = SearchFilterViewSessionManager.get(chatId);
        if (oldFilter.getFilterMessageId() != null) {
            telegramClient.deleteMessage(chatId, oldFilter.getFilterMessageId());
        }
        SearchFilterViewSessionManager.remove(chatId);
    }

    Integer filterMessageId = telegramClient.sendSearchFilterKeyboardWithMessageId(chatId, activeFilter);
    SearchFilterViewSessionManager.save(chatId, new SearchFilterViewSession(filterMessageId));
}
protected boolean handleRegistrationLocationText(Long chatId, String text) {
    if (!RegistrationSessionManager.exists(chatId)) {
        return false;
    }

    RegistrationSession session = RegistrationSessionManager.get(chatId);
    if (session == null || session.getStep() != RegistrationStep.LOCATION) {
        return false;
    }

    String value = text == null ? "" : text.trim();
    String normalized = value.toLowerCase();

    // Handle landmark input / skip
    if (session.isWaitingForLandmark()) {
        if (normalized.equals("⏭ skip landmark")) {
            session.setLandmark(null);
        } else {
            session.setLandmark(value);
        }
        session.clearLocationFlags();
        telegramClient.sendMessage(chatId, "Step 7/7\n📄 Now upload your pharmacy license.");
        return true;
    }

if (normalized.equals("📍 share exact pharmacy location".toLowerCase())
        || normalized.equals("📍 send pharmacy location".toLowerCase())) {
    session.setExactLocationMode();
    telegramClient.sendExactPharmacyLocationRequest(chatId);
    return true;
}

    if (normalized.equals("🔗 paste google maps link".toLowerCase())) {
        session.setGoogleMapMode();
        telegramClient.sendMessage(chatId, "🔗 Paste the Google Maps link or coordinates.\n\nExample:\n9.0320, 38.7360");
        return true;
    }

    if (normalized.equals("🗺 select ethiopia region".toLowerCase())) {
        session.setRegionMode();
        telegramClient.sendRegionKeyboard(chatId);
        return true;
    }

   if (normalized.equals(BACK_ARROW.toLowerCase())) {
    if (session.isWaitingForAreaSelection()) {
        session.setArea(null);

        if (EthiopiaLocationCatalog.isAddisAbabaCity(session.getSelectedCity())) {
            session.setSubCityMode();
            telegramClient.sendSubCityKeyboard(
                    chatId,
                    session.getSelectedCity(),
                    EthiopiaLocationCatalog.getAddisAbabaSubCities()
            );
        } else {
            session.setCityMode();
            telegramClient.sendCityKeyboard(
                    chatId,
                    session.getSelectedRegion(),
                    EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion())
            );
        }
        return true;
    }

    if (session.isWaitingForSubCitySelection()) {
        session.setSelectedSubCity(null);
        session.setCityMode();
        telegramClient.sendCityKeyboard(
                chatId,
                session.getSelectedRegion(),
                EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion())
        );
        return true;
    }

    if (session.isWaitingForCitySelection()) {
        session.setSelectedCity(null);
        session.setRegionMode();
        telegramClient.sendRegionKeyboard(chatId);
        return true;
    }

    session.clearLocationFlags();
    telegramClient.sendLocationChoiceMenu(chatId);
    return true;
}

    if (normalized.equals(MAIN_HOME.toLowerCase())) {
        session.clearLocationFlags();
        RegistrationSessionManager.remove(chatId);
        restoreKeyboard(chatId);
        return true;
    }

    if (normalized.equals("❌ cancel".toLowerCase())) {
        session.clearLocationFlags();
        RegistrationSessionManager.remove(chatId);
        telegramClient.sendMessage(chatId, "❌ Registration cancelled.");
        restoreKeyboard(chatId);
        return true;
    }

    if (session.isWaitingForLandmark()) {
        if (normalized.equals("⏭ skip landmark")) {
            session.setWaitingForLandmark(false);
            session.setLandmark(null);
        } else {
            session.setLandmark(value);
            session.setWaitingForLandmark(false);
        }
        telegramClient.sendMessage(chatId, "Step 7/7\n📄 Now upload your pharmacy license.");
        return true;
    }

    if (session.isWaitingForGoogleMapLink()) {
        double[] coords = parseCoordinates(value);
        if (coords == null) {
            telegramClient.sendMessage(chatId, "⚠️ Could not read coordinates.\nSend valid lat,lng or Google Maps link.");
            return true;
        }

        session.setLatitude(coords[0]);
        session.setLongitude(coords[1]);

        EthiopiaLocationOption nearest = EthiopiaLocationCatalog.findNearest(coords[0], coords[1]);
        if (nearest != null) {
            session.setSelectedRegion(nearest.getRegion());
            session.setSelectedCity(nearest.getCity());
            session.setArea(nearest.getArea());

            if (EthiopiaLocationCatalog.isAddisAbabaRegion(nearest.getRegion())) {
                session.setSelectedSubCity(EthiopiaLocationCatalog.findAddisSubCityByArea(nearest.getArea()));
            }
        }

        finalizeRegistrationLocation(chatId, session);
        return true;
    }

    if (session.isWaitingForRegionSelection()) {
        if (EthiopiaLocationCatalog.getRegions().stream().noneMatch(r -> r.equalsIgnoreCase(value))) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid region from the keyboard.");
            return true;
        }

        session.setSelectedRegion(value);
        session.setCityMode();
        telegramClient.sendCityKeyboard(
                chatId,
                value,
                EthiopiaLocationCatalog.getCitiesByRegion(value)
        );
        return true;
    }

    if (session.isWaitingForCitySelection()) {
        var cities = EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion());

        if (cities.stream().noneMatch(c -> c.equalsIgnoreCase(value))) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid city from the keyboard.");
            return true;
        }

        session.setSelectedCity(value);

        if (EthiopiaLocationCatalog.isAddisAbabaCity(value)) {
            session.setSubCityMode();
            telegramClient.sendSubCityKeyboard(
                    chatId,
                    value,
                    EthiopiaLocationCatalog.getAddisAbabaSubCities()
            );
            return true;
        }

        session.setAreaMode();
        telegramClient.sendAreaKeyboard(
                chatId,
                value,
                EthiopiaLocationCatalog.getAreasByRegionAndCity(session.getSelectedRegion(), value)
        );
        return true;
    }

    if (session.isWaitingForSubCitySelection()) {
        var subCities = EthiopiaLocationCatalog.getAddisAbabaSubCities();

        if (subCities.stream().noneMatch(sc -> sc.equalsIgnoreCase(value))) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid sub-city from the keyboard.");
            return true;
        }

        session.setSelectedSubCity(value);
        session.setAreaMode();
        telegramClient.sendAreaKeyboard(
                chatId,
                value,
                EthiopiaLocationCatalog.getAddisAreasBySubCity(value)
        );
        return true;
    }
if (session.isWaitingForAreaSelection()) {
    List<String> areas;

    if (EthiopiaLocationCatalog.isAddisAbabaCity(session.getSelectedCity())) {
        areas = EthiopiaLocationCatalog.getAddisAreasBySubCity(session.getSelectedSubCity());
    } else {
        areas = EthiopiaLocationCatalog.getAreasByRegionAndCity(
                session.getSelectedRegion(),
                session.getSelectedCity()
        );
    }

    if (areas.stream().noneMatch(a -> a.equalsIgnoreCase(value))) {
        telegramClient.sendMessage(chatId, "⚠️ Please choose a valid area from the keyboard.");
        return true;
    }

    session.setArea(value);

    EthiopiaLocationOption selected = EthiopiaLocationCatalog.find(
            session.getSelectedRegion(),
            session.getSelectedCity(),
            session.getArea()
    );

    if (selected != null) {
        // overwrite old exact-location coordinates with Ethiopia catalog coordinates
        session.setLatitude(selected.getLatitude());
        session.setLongitude(selected.getLongitude());
        session.setFormattedAddress(selected.getArea() + ", " + selected.getCity());
    } else {
        // fallback: do not keep wrong old coordinates
        session.setLatitude(null);
        session.setLongitude(null);
        session.setFormattedAddress(session.getArea() + ", " + session.getSelectedCity());
    }

    finalizeRegistrationLocation(chatId, session);
    return true;
}

    if (session.isWaitingForLandmark()) {
        if (normalized.equals("⏭ skip landmark")) {
            session.setWaitingForLandmark(false);
        } else {
            session.setLandmark(value);
            session.setWaitingForLandmark(false);
        }
        telegramClient.sendMessage(chatId, "Step 7/7\n📄 Now upload your pharmacy license.");
        return true;
    }

    return false;
}
protected void finalizeRegistrationLocation(Long chatId, RegistrationSession session) {
    session.clearLocationFlags();

    if (session.getSelectedCity() != null) {
        session.setCity(session.getSelectedCity());
    }

    if ((session.getLatitude() == null || session.getLongitude() == null)
            && session.getSelectedRegion() != null
            && session.getSelectedCity() != null
            && session.getArea() != null) {

        EthiopiaLocationOption option = EthiopiaLocationCatalog.find(
                session.getSelectedRegion(),
                session.getSelectedCity(),
                session.getArea()
        );

        if (option != null) {
            session.setLatitude(option.getLatitude());
            session.setLongitude(option.getLongitude());
        }
    }

    StringBuilder summary = new StringBuilder("✅ <b>Pharmacy location saved</b>\n\n");

    if (session.getSelectedRegion() != null) {
        summary.append("🗺 Region: ").append(session.getSelectedRegion()).append("\n");
    }
    if (session.getSelectedCity() != null) {
        summary.append("🏙 City: ").append(session.getSelectedCity()).append("\n");
    }
    if (session.getSelectedSubCity() != null && !session.getSelectedSubCity().isBlank()) {
        summary.append("🏢 Sub-City: ").append(session.getSelectedSubCity()).append("\n");
    }
    if (session.getArea() != null) {
        summary.append("📍 Area: ").append(session.getArea()).append("\n");
    }
    if (session.getFormattedAddress() != null && !session.getFormattedAddress().isBlank()) {
        summary.append("📍 Exact Address: ").append(session.getFormattedAddress()).append("\n");
    }
    if (session.getLandmark() != null && !session.getLandmark().isBlank()) {
        summary.append("🏢 Landmark: ").append(session.getLandmark()).append("\n");
    }
    if (session.getPlusCode() != null && !session.getPlusCode().isBlank()) {
        summary.append("➕ Plus Code: ").append(session.getPlusCode()).append("\n");
    }
    if (session.getLatitude() != null && session.getLongitude() != null) {
        summary.append("📌 Latitude: ").append(session.getLatitude()).append("\n");
        summary.append("📌 Longitude: ").append(session.getLongitude()).append("\n");
    }

    telegramClient.sendMessage(chatId, summary.toString(), "HTML");

    // ask landmark before moving to Step 7/7
    if (session.getLandmark() == null || session.getLandmark().isBlank()) {
        session.setWaitingForLandmark(true);

        telegramClient.sendMessage(
                chatId,
                "🏢 <b>Add a nearby landmark</b>\n\n" +
                "This helps customers find the pharmacy more easily.\n\n" +
                "Examples:\n" +
                "• Near Bole Mikael Bridge\n" +
                "• Behind Total Fuel Station\n" +
                "• Next to Commercial Bank\n\n" +
                "Send a landmark now or tap Skip.",
                "HTML"
        );

        telegramClient.sendLandmarkChoiceKeyboard(chatId);
        return;
    }

    telegramClient.sendMessage(
            chatId,
            "Step 7/7\n📄 Now upload your pharmacy license."
    );
}
protected double[] parseCoordinates(String input) {
    if (input == null || input.isBlank()) {
        return null;
    }

    String value = input.trim();

    try {
        if (value.matches("^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$")) {
            String[] parts = value.split(",");
            return new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim())
            };
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)")
                .matcher(value);

        if (matcher.find()) {
            return new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2))
            };
        }
    } catch (Exception ignored) {
    }

    return null;
}
protected boolean shouldUseEthiopiaCatalog(double lat, double lon, EthiopiaLocationOption nearest) {
    if (nearest == null) {
        return false;
    }

    double distanceKm = com.tenahub.bot.util.GeoUtils.distance(
            lat,
            lon,
            nearest.getLatitude(),
            nearest.getLongitude()
    );

    // only trust nearest Ethiopia match if it is reasonably close
    return distanceKm <= 50.0;
}


}
