package com.tenahub.bot.handler;

import com.tenahub.bot.dto.EthiopiaLocationOption;
import com.tenahub.bot.dto.MedicineSuggestionResult;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.AdminAuditTrail;
import com.tenahub.bot.entity.AdminInboxItemStatus;
import com.tenahub.bot.entity.AdminInboxItemType;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.registration.AdminPharmacyEditField;
import com.tenahub.bot.registration.AdminPharmacyEditSession;
import com.tenahub.bot.registration.AdminPharmacyEditSessionManager;
import com.tenahub.bot.registration.AdminPharmacyManagementSession;
import com.tenahub.bot.registration.AdminPharmacyManagementSessionManager;
import com.tenahub.bot.registration.AdminRejectSession;
import com.tenahub.bot.registration.AdminRejectSessionManager;
import com.tenahub.bot.registration.AdminRejectType;
import com.tenahub.bot.registration.AdminReservationSession;
import com.tenahub.bot.registration.AdminReservationSessionManager;
import com.tenahub.bot.registration.AdminViewSession;
import com.tenahub.bot.registration.AdminViewSessionManager;
import com.tenahub.bot.registration.LocationFlowType;
import com.tenahub.bot.registration.LocationSelectionSession;
import com.tenahub.bot.registration.LocationSelectionSessionManager;
import com.tenahub.bot.registration.MedicineSearchSession;
import com.tenahub.bot.registration.MedicineSearchSessionManager;
import com.tenahub.bot.registration.MedicineSelectionSession;
import com.tenahub.bot.registration.MedicineSelectionSessionManager;
import com.tenahub.bot.registration.MultiMedicineSearchSession;
import com.tenahub.bot.registration.MultiMedicineSearchSessionManager;
import com.tenahub.bot.registration.MultiReservationSession;
import com.tenahub.bot.registration.MultiReservationSessionManager;
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
import com.tenahub.bot.service.AdminInboxService;
import com.tenahub.bot.service.AdminAuditTrailService;
import com.tenahub.bot.service.FavoritePharmacyService;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.MedicinePhotoService;
import com.tenahub.bot.service.MedicineSearchLogService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.service.PharmacyPhotoService;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.RatingService;
import com.tenahub.bot.service.RegistrationService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationWorkflowService;
import com.tenahub.bot.service.UserLocationService;
import com.tenahub.bot.util.BotLanguage;
import com.tenahub.bot.util.EthiopiaLocationCatalog;
import com.tenahub.bot.util.EthiopiaLocationTranslator;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TelegramWebhookFacade {

    private final PharmacyService pharmacyService;
    private final PharmacyPerformanceService pharmacyPerformanceService;
    private final PharmacyPhotoService pharmacyPhotoService;
    private final RegistrationService registrationService;
    private final TelegramClient telegramClient;
    private final LocalizationService localizationService;
    private final UserLocationService userLocationService;
    private final RatingService ratingService;
    private final PharmacyRepository pharmacyRepository;
    private final InventoryService inventoryService;
    private final ReservationService reservationService;
    private final ReservationWorkflowService reservationWorkflowService;
    private final PrescriptionReviewService prescriptionReviewService;
    private final AdminService adminService;
    private final AdminInboxService adminInboxService;
    private final AdminAuditTrailService adminAuditTrailService;
    private final LicenseComplianceService licenseComplianceService;
    private final PharmacyInventoryRepository inventoryRepository;
    private final com.tenahub.bot.repository.MedicineReservationRepository reservationRepository;
    private final FavoritePharmacyService favoritePharmacyService;
    private final MedicineSearchLogService medicineSearchLogService;
    private final MedicineAvailabilityAlertService medicineAvailabilityAlertService;
    private final MedicinePhotoService medicinePhotoService;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private long pendingTimeoutMinutes;

    @Value("${tenahub.info.website:N/A}")
    private String infoWebsite;

    @Value("${tenahub.info.support-phone:N/A}")
    private String infoSupportPhone;

    @Value("${tenahub.info.support-email:N/A}")
    private String infoSupportEmail;

    @Value("${tenahub.info.telegram-username:}")
    private String infoTelegramUsername;

    @Value("${tenahub.info.partnership-contact:}")
    private String infoPartnershipContact;

    private final Long ADMIN_CHAT_ID = 8251771745L;

    private static final String PHARMACY_NOT_FOUND = "Pharmacy not found";
    private static final String WARN_PREFIX = "⚠️ ";
    private static final String MEDICINE_LABEL = "💊 Medicine: ";
    private static final String QUANTITY_LABEL = "🔢 Quantity: ";
    private static final String BACK_ARROW = "⬅️ back";
    private static final String BACK_SYMBOL = "🔙 back";
    private static final String MAIN_HOME = "🏠 main";
    private static final String NEAREST = "Nearest";
    private static final String PHONE_LABEL = "📱 Phone: ";
    private static final String PENDING = "PENDING";

    private final Map<Long, PendingIssueReport> pendingIssueReports = new ConcurrentHashMap<>();
    private final Map<String, Long> submittedIssueReports = new ConcurrentHashMap<>();
    private final Map<Long, String> adminInboxLastListFilter = new ConcurrentHashMap<>();
    private final Map<Long, String> adminLicenseComplianceLastCategory = new ConcurrentHashMap<>();
    private final Map<Long, String> adminLicenseCompliancePendingAction = new ConcurrentHashMap<>();
    private final Map<Long, Long> adminLicenseComplianceCurrentPharmacy = new ConcurrentHashMap<>();
    private final Map<Long, String> adminAuditTrailLastFilter = new ConcurrentHashMap<>();
    private final Map<Long, String> adminInboxPendingAction = new ConcurrentHashMap<>();
    private final Map<Long, Long> adminInboxCurrentItemId = new ConcurrentHashMap<>();
    private final Map<Long, String> pharmacyPhotoPendingAction = new ConcurrentHashMap<>();
    private final Map<Long, String> medicinePhotoPendingAction = new ConcurrentHashMap<>();
    private final Map<Long, Long> medicinePhotoPendingMedicineId = new ConcurrentHashMap<>();
    private final Set<Long> awaitingFeedback = ConcurrentHashMap.newKeySet();
    private static final long ISSUE_REPORT_COOLDOWN_MS = 24 * 60 * 60 * 1000L;

    private record PendingIssueReport(Long pharmacyId, String medicineName) {}

    private String displayMedicine(Long chatId, String medicineName) {
        return telegramClient.displayMedicine(chatId, medicineName);
    }

    private String displayLocation(Long chatId, String locationValue) {
        return EthiopiaLocationTranslator.toDisplayValue(locationValue, localizationService.getLanguage(chatId));
    }

    private String displayLocationAddress(Long chatId, String locationValue) {
        return EthiopiaLocationTranslator.toDisplayAddress(locationValue, localizationService.getLanguage(chatId));
    }

    public void handleCallback(TelegramUpdateDTO update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChat().getId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        String callbackId = update.getCallbackQuery().getId();
        if (handleAlertRecentCallback(chatId, messageId, data, callbackId)) return;

        if (handleFavoritesCallback(chatId, messageId, data, callbackId)) return;

        if (handleTemporaryCloseCallback(chatId, messageId, data, callbackId)) return;

        if (handleExpiryPickerCallback(chatId, messageId, data, callbackId)) return;

        if (data.equals("set_lang_en") || data.equals("set_lang_am")) {
            telegramClient.answerCallback(callbackId);
            BotLanguage chosen = data.equals("set_lang_am") ? BotLanguage.AMHARIC : BotLanguage.ENGLISH;
            localizationService.setLanguage(chatId, chosen);
            String confirm = chosen == BotLanguage.AMHARIC
                    ? "🌐 ቋንቋ ወደ አማርኛ ተቀይሯl።"
                    : "🌐 Language switched to English.";
            telegramClient.sendMessage(chatId, confirm);
            telegramClient.sendUserDashboard(chatId);
            return;
        }

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

    private boolean handleTemporaryCloseCallback(Long chatId, Integer messageId, String data, String callbackId) {
        if (data.equals("temp_close_cancel")) {
            telegramClient.answerCallback(callbackId);
            UpdateSessionManager.remove(chatId);
            telegramClient.sendMessage(chatId, "❌ Temporary close cancelled.");
            return true;
        }

        if (data.equals("temp_close_reopen_now")) {
            telegramClient.answerCallback(callbackId);
            try {
                pharmacyService.clearTemporaryClosure(chatId);
                UpdateSessionManager.remove(chatId);
                telegramClient.sendMessage(chatId, "✅ Pharmacy reopened. Reservations are enabled again.");
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }

        if (data.startsWith("temp_close_duration_")) {
            telegramClient.answerCallback(callbackId);
            int durationHours;
            try {
                durationHours = Integer.parseInt(data.substring("temp_close_duration_".length()));
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid duration selection.");
                return true;
            }

            if (durationHours <= 0 || durationHours > 168) {
                telegramClient.sendMessage(chatId, "⚠️ Hours must be between 1 and 168.");
                return true;
            }

            UpdateSessionManager.start(chatId, UpdateField.TEMP_CLOSE);
            UpdateSessionManager.get(chatId).setTempHour(durationHours);
            telegramClient.sendTemporaryCloseReasonPicker(chatId, durationHours);
            return true;
        }

        if (data.startsWith("temp_close_reason_")) {
            telegramClient.answerCallback(callbackId);

            if (!UpdateSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired. Please start Temporary Close again.");
                return true;
            }

            UpdateSession session = UpdateSessionManager.get(chatId);
            if (session.getField() != UpdateField.TEMP_CLOSE || session.getTempHour() == null) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired. Please start Temporary Close again.");
                UpdateSessionManager.remove(chatId);
                return true;
            }

            String reasonKey = data.substring("temp_close_reason_".length());
            if ("other".equals(reasonKey)) {
                telegramClient.sendMessage(
                        chatId,
                        "✍️ Send your custom temporary-closure reason as text."
                );
                return true;
            }

            String reason = switch (reasonKey) {
                case "power" -> "Power outage";
                case "maintenance" -> "Maintenance";
                case "refill" -> "Stock refill";
                case "staff" -> "Staff shortage";
                case "holiday" -> "Holiday/Break";
                default -> null;
            };

            if (reason == null) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid reason selection.");
                return true;
            }

            int durationHours = session.getTempHour();
            pharmacyService.setTemporaryClosure(chatId, reason, durationHours);
            UpdateSessionManager.remove(chatId);

            telegramClient.sendMessage(
                    chatId,
                    "🛑 Pharmacy temporarily closed for " + durationHours + " hour(s).\n" +
                            "Reason: " + reason + "\n\n" +
                            "Use ✅ Reopen Now anytime to open again."
            );
            return true;
        }

        return false;
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

private void handleContactMessage(TelegramUpdateDTO update, Long chatId) {

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

        try {
            reservationWorkflowService.notifyPharmacyPendingReservation(reservation, pendingTimeoutMinutes);

            telegramClient.sendMessageRemoveKeyboard(
                    chatId,
                    "✅ Reservation request sent to pharmacy.\n\n"
                        + MEDICINE_LABEL + displayMedicine(chatId, session.getMedicineName()) + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + phone + "\n"
                            + "🕒 Waiting for pharmacy approval.\n"
                            + "⏱ Auto-cancels in " + pendingTimeoutMinutes + " minutes if not approved."
            );

            restoreReservationExitKeyboard(chatId);

        } catch (Exception notifyError) {
            telegramClient.sendMessageRemoveKeyboard(
                    chatId,
                    "✅ Reservation saved.\n\n"
                        + MEDICINE_LABEL + displayMedicine(chatId, session.getMedicineName()) + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + phone + "\n\n"
                            + "⚠️ Could not notify the pharmacy automatically."
            );

            restoreReservationExitKeyboard(chatId);
        }

    } catch (Exception createError) {
        telegramClient.sendMessageRemoveKeyboard(
                chatId,
                "⚠️ Could not create reservation.\n\n" + createError.getMessage()
        );

        restoreReservationExitKeyboard(chatId);
    } finally {
        ReservationSessionManager.remove(chatId);
    }
}
private void handleLocationMessage(TelegramUpdateDTO update, Long chatId) {
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

        // 4) ADMIN EDIT PHARMACY EXACT LOCATION
        if (chatId.equals(ADMIN_CHAT_ID) && AdminPharmacyEditSessionManager.exists(chatId)) {
            AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
            if (editSession != null && editSession.getField() == AdminPharmacyEditField.LOCATION_EXACT) {
                EthiopiaLocationOption nearest = resolveNearestCatalogLocation(lat, lon);

                if (nearest != null) {
                    adminService.updatePharmacyLocation(
                            editSession.getPharmacyId(),
                            lat,
                            lon,
                            nearest.getCity(),
                            nearest.getArea()
                    );
                } else {
                    adminService.updatePharmacyLocation(
                            editSession.getPharmacyId(),
                            lat,
                            lon,
                            "Exact Location",
                            "Exact Location"
                    );
                }

                AdminPharmacyEditSessionManager.remove(chatId);
                telegramClient.sendMessage(chatId, "✅ Pharmacy exact location updated.");
                refreshAdminPharmacyDetail(chatId, editSession.getPharmacyId(), null);
                return;
            }
        }

        // 5) NORMAL USER LOCATION
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
  private void handlePhotoMessage(TelegramUpdateDTO update, Long chatId) {
    var photos = update.getMessage().getPhoto();

    if (photos == null || photos.isEmpty()) {
        return;
    }

    String fileId = photos.get(photos.size() - 1).getFileId();
if (UpdateSessionManager.exists(chatId)) {
    UpdateSession session = UpdateSessionManager.get(chatId);

    if (session.getField() == UpdateField.PHOTO || session.getField() == UpdateField.PHOTO_ADD) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
            pharmacyPhotoService.addPhoto(pharmacy.getId(), fileId, null);

            int count = pharmacyPhotoService.listByPharmacyId(pharmacy.getId()).size();
            telegramClient.sendMessage(chatId, "✅ Photo added (" + count + "/" + PharmacyPhotoService.MAX_PHOTOS + ")");
            UpdateSessionManager.remove(chatId);
            telegramClient.sendPharmacyPhotoManagementMenu(chatId);
            return;

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            telegramClient.sendPharmacyPhotoManagementMenu(chatId);
            return;
        }
    }

    if (session.getField() == UpdateField.MEDICINE_PHOTO_ADD) {
        try {
            Long medicineId = medicinePhotoPendingMedicineId.get(chatId);
            if (medicineId == null) {
                throw new RuntimeException("No medicine selected. Start medicine photo management again.");
            }

            PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
            medicinePhotoService.addPhoto(medicine.getId(), fileId, null);

            int count = medicinePhotoService.listByMedicineId(medicine.getId()).size();
            telegramClient.sendMessage(chatId,
                    "✅ Medicine photo added (" + count + "/" + MedicinePhotoService.MAX_PHOTOS + ") for "
                            + displayMedicine(chatId, medicine.getMedicineName()));
            UpdateSessionManager.remove(chatId);
            medicinePhotoPendingAction.remove(chatId);
            medicinePhotoPendingMedicineId.remove(chatId);
            telegramClient.sendMedicinePhotoManagementMenu(chatId);
            return;

        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            UpdateSessionManager.remove(chatId);
            medicinePhotoPendingAction.remove(chatId);
            medicinePhotoPendingMedicineId.remove(chatId);
            telegramClient.sendMedicinePhotoManagementMenu(chatId);
            return;
        }
    }
}
    // ---------------- LICENSE UPDATE FOR EXISTING PHARMACY ----------------
    if (UpdateSessionManager.exists(chatId)) {
        UpdateSession session = UpdateSessionManager.get(chatId);

        if (session.getField() == UpdateField.LICENSE) {
            session.setPendingFileId(fileId);
            session.setWaitingForLicenseExpiryDate(true);
            telegramClient.sendLicenseUpdateExpiryPrompt(chatId);
            return;
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
        if (session.getLicenseExpiryDate() == null) {
            telegramClient.sendRegistrationLicenseExpiryPrompt(chatId);
            return;
        }
        registrationService.saveLicenseExpiryDate(chatId, session.getLicenseExpiryDate());

        Long registrationId = registrationService.saveLicense(chatId, fileId);
        var reg = registrationService.getRegistration(registrationId);

        String caption = "🆕 <b>New Pharmacy Registration</b>\n\n"
                + "🏥 <b>Name:</b> " + reg.getName() + "\n"
            + "🏙️ <b>City:</b> " + displayLocation(ADMIN_CHAT_ID, reg.getCity()) + "\n"
            + "📍 <b>Area:</b> " + displayLocation(ADMIN_CHAT_ID, reg.getArea()) + "\n"
                + "📞 <b>Phone:</b> " + reg.getPhone() + "\n"
                + "💊 <b>Medicines:</b> " + reg.getMedicines() + "\n"
                + "🕒 <b>Open:</b> " + reg.getOpenTime() + "\n"
                + "🌙 <b>Close:</b> " + reg.getCloseTime() + "\n"
                + "📅 <b>License Expiry:</b> " + reg.getLicenseExpiryDate() + "\n"
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

    private void handleTextMessage(TelegramUpdateDTO update, Long chatId) {
    String text = update.getMessage().getText();

    if (text == null) {
        return;
    }

    String originalText = text.trim();
    String normalizedText = originalText.toLowerCase();
    // Normalize Amharic navigation buttons to English equivalents for handler matching
    if ("🏠 ዋናው ገጽ".equals(normalizedText)) normalizedText = MAIN_HOME;
    if ("🔄 አድስ".equals(normalizedText)) normalizedText = "🔄 refresh";
    if ("❤️ ተወዳጅ ፋርማሲዎች".equals(normalizedText)) normalizedText = "❤️ favorite pharmacies";
    if ("⚙️ ፕሮፋይል".equals(normalizedText)) normalizedText = "⚙️ profile";
    if ("⬅️ ተመለስ".equals(normalizedText)) normalizedText = BACK_ARROW;
    if ("🔙 ተመለስ".equals(normalizedText)) normalizedText = BACK_SYMBOL;
    if ("❌ ሰርዝ".equals(normalizedText)) normalizedText = "❌ cancel";
    if ("📍 ትክክለኛ የፋርማሲ አካባቢ አጋራ".equals(normalizedText)) normalizedText = "📍 share exact pharmacy location";
    if ("🔗 የgoogle maps ሊንክ ለጥፍ".equals(normalizedText)) normalizedText = "🔗 paste google maps link";
    if ("🗺 የኢትዮጵያ ክልል ምረጥ".equals(normalizedText)) normalizedText = "🗺 select ethiopia region";
    if ("📍 ተቀምጦ ያለ አካባቢ ጠቀም".equals(normalizedText)) normalizedText = "📍 use saved location";
    if ("📌 አሁን ያለህ አካባቢ አጋራ".equals(normalizedText)) normalizedText = "📌 share current location";
    if ("📍 አካባቢ ቀይር".equals(normalizedText)) normalizedText = "📍 change location";
    if ("➕ ተጨማሪ ጨምር".equals(normalizedText)) normalizedText = "➕ add more";
    if ("🗑 አጽዳ".equals(normalizedText)) normalizedText = "🗑 clear";
    if ("🔍 ፋርማሲዎች ፈልግ".equals(normalizedText)) normalizedText = "🔍 search pharmacies";
    if ("📍 ቅርብ".equals(normalizedText)) normalizedText = "📍 nearest";
    if ("📍 ✅ ቅርብ".equals(normalizedText)) normalizedText = "📍 ✅ nearest";
    if ("💰 ዝቅተኛ ዋጋ".equals(normalizedText)) normalizedText = "💰 cheapest";
    if ("💰 ✅ ዝቅተኛ ዋጋ".equals(normalizedText)) normalizedText = "💰 ✅ cheapest";
    if ("⭐ ከፍተኛ ደረጃ".equals(normalizedText)) normalizedText = "⭐ highest rated";
    if ("⭐ ✅ ከፍተኛ ደረጃ".equals(normalizedText)) normalizedText = "⭐ ✅ highest rated";
    if ("🟢 አሁን ክፍት".equals(normalizedText)) normalizedText = "🟢 open now";
    if ("🟢 ✅ አሁን ክፍት".equals(normalizedText)) normalizedText = "🟢 ✅ open now";
    if ("📦 በስቶክ ያሉ ብቻ".equals(normalizedText)) normalizedText = "📦 in stock only";
    if ("📦 ✅ በስቶክ ያሉ ብቻ".equals(normalizedText)) normalizedText = "📦 ✅ in stock only";
    if ("❌ ማጣሪያዎችን አጥፋ".equals(normalizedText)) normalizedText = "❌ clear filters";
    if ("⏳ በመጠባበቅ ላይ".equals(normalizedText)) normalizedText = "⏳ pending";
    if ("📦 የተጠናቀቀ".equals(normalizedText)) normalizedText = "📦 fulfilled";
    if ("⌛ ያለፈበት".equals(normalizedText)) normalizedText = "⌛ expired";
    if ("❌ የተሰረዘ".equals(normalizedText)) normalizedText = "❌ cancelled";
    if ("📍 ትክክለኛ አካባቢ አጋራ".equals(normalizedText)) normalizedText = "📍 share exact location";
    if ("⏭ ምልክተኛ ዝለል".equals(normalizedText)) normalizedText = "⏭ skip landmark";
    if ("⏭ ፕለስ ኮድ ዝለል".equals(normalizedText)) normalizedText = "⏭ skip plus code";
    if ("⏭ ትክክለኛ አድራሻ ዝለል".equals(normalizedText)) normalizedText = "⏭ skip exact address";

    PendingIssueReport pendingIssue = pendingIssueReports.get(chatId);
    if (pendingIssue != null) {
        if (originalText.startsWith("/")) {
            pendingIssueReports.remove(chatId);
            telegramClient.sendMessage(chatId, "❌ Issue report cancelled.");
            return;
        }

        pendingIssueReports.remove(chatId);
        submitIssueReport(chatId, pendingIssue.pharmacyId(), pendingIssue.medicineName(), "other", originalText);
        return;
    }

    if (awaitingFeedback.remove(chatId)) {
        notifyAdminFeedback(chatId, originalText);
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "feedback_received"));
        return;
    }

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
        medicinePhotoPendingAction.remove(chatId);
        medicinePhotoPendingMedicineId.remove(chatId);
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
        clearAdminPharmacyManagementState(chatId);
    clearAdminPharmacyEditState(chatId);
    AdminReservationSessionManager.remove(chatId);

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
    if (chatId.equals(ADMIN_CHAT_ID)
            && (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL))
            && AdminPharmacyManagementSessionManager.exists(chatId)) {
        clearAdminPharmacyManagementState(chatId);
        clearAdminPharmacyEditState(chatId);
        telegramClient.sendAdminDashboard(chatId);
        return;
    }

    if (chatId.equals(ADMIN_CHAT_ID)
            && (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL))
            && AdminPharmacyEditSessionManager.exists(chatId)) {
        clearAdminPharmacyEditState(chatId);
        telegramClient.sendAdminDashboard(chatId);
        return;
    }

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
    if (normalizedText.equals("🖼 manage photos") || normalizedText.equals("🖼 update pharmacy photo")) {
        telegramClient.sendPharmacyPhotoManagementMenu(chatId);
        return;
    }

    if (normalizedText.equals("💊 manage medicine photos")) {
        telegramClient.sendMedicinePhotoManagementMenu(chatId);
        return;
    }

    if (normalizedText.equals("➕ add medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "add");
        return;
    }

    if (normalizedText.equals("👁 view medicine photos")) {
        startMedicinePhotoSelectionFlow(chatId, "view");
        return;
    }

    if (normalizedText.equals("⭐ set main medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "set_main");
        return;
    }

    if (normalizedText.equals("🗑 remove medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "remove");
        return;
    }

    if (normalizedText.equals("➕ add photo")) {
        telegramClient.sendMessage(chatId, "Send one photo now. You can upload up to 4 photos.");
        UpdateSessionManager.start(chatId, UpdateField.PHOTO_ADD);
        return;
    }

    if (normalizedText.equals("👁 view photos")) {
        showPharmacyOwnPhotos(chatId);
        return;
    }

    if (normalizedText.equals("⭐ set main photo")) {
        startSetMainPhotoFlow(chatId);
        return;
    }

    if (normalizedText.equals("🗑 remove photo")) {
        startRemovePhotoFlow(chatId);
        return;
    }
    if (normalizedText.equals("🕘 recent searches") || normalizedText.equals("🕘 የቅርብ ፍለጋዎች")) {
    telegramClient.sendRecentSearches(chatId, medicineSearchLogService.getRecentSearches(chatId));
    return;
}

if (normalizedText.equals("🔔 my alerts") || normalizedText.equals("🔔 ማስጠንቀቂያዎቼ")) {
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
        registrationService.saveLocation(chatId, regLat, regLon, null, null, null);

        telegramClient.sendLocation(chatId, regLat, regLon);
        finalizeRegistrationLocation(chatId, session);
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
                recordAudit(chatId, "PHARMACY_REJECTED", "REGISTRATION", registrationId,
                    "Registration rejected. Reason: " + reason);

                telegramClient.sendMessage(chatId, "❌ Registration rejected and reason sent.");
                telegramClient.sendRejectedRegistrationResumeMenu(reg.getTelegramId(), reason);
            } else if (rejectSession.getType() == AdminRejectType.LICENSE_UPDATE) {
                Long pharmacyTelegramId = rejectSession.getTargetId();

                pharmacyService.rejectPendingLicenseUpdate(pharmacyTelegramId);
                recordAudit(chatId, "LICENSE_UPDATE_REJECTED", "PHARMACY_TELEGRAM", pharmacyTelegramId,
                    "License update rejected. Reason: " + reason);

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
            String medicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(originalText);

            if (medicine.isBlank()) {
                telegramClient.sendMessage(chatId, "⚠️ Medicine name cannot be empty.");
                return;
            }

            MedicineSuggestionResult suggestionResult = pharmacyService.suggestMedicineOptions(medicine);

            if (suggestionResult != null && suggestionResult.hasSuggestions()) {
                telegramClient.sendMedicineSuggestions(chatId, suggestionResult, medicine);
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

        if (normalizedText.startsWith("❌ ") && !normalizedText.equals("❌ cancel")) {
            String label = normalizedText.substring("❌ ".length()).trim();
            List<String> meds = session.getSelectedMedicines();
            meds.removeIf(x ->
                    x.equalsIgnoreCase(label) ||
                    telegramClient.displayMedicine(chatId, x).equalsIgnoreCase(label)
            );
            if (meds.isEmpty()) {
                session.setWaitingForMedicineInput(true);
                telegramClient.sendMessage(chatId, "🗑 All medicines removed.\n\nNow send the first medicine name.");
            } else {
                telegramClient.sendMultiMedicinePanel(chatId, meds);
            }
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

            String medicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(originalText);

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

            telegramClient.sendMessage(chatId, "✅ Added: " + displayMedicine(chatId, medicine));
            telegramClient.sendMultiMedicinePanel(chatId, session.getSelectedMedicines());
            return;
        }
    }

    // ---------------- GENERAL MENU ----------------
    if (normalizedText.equals("🔎🛒 search multiple meds") || normalizedText.equals("🔎🛒 ብዙ መድሃኒቶች ፈልግ")) {
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
    

    if (normalizedText.equals("🔎 search medicines") || normalizedText.equals("🔎 search medicine") || normalizedText.equals("🔎 መድሃኒት ፈልግ")) {
            clearOpenDetailExtras(chatId);

        telegramClient.sendMessage(chatId, "🔎 Send medicine name to search.");
        return;
    }

 if (normalizedText.equals("👤 account") || normalizedText.equals("👤 መለያ")) {
    telegramClient.sendMessage(chatId, buildUserAccountView(chatId));
    telegramClient.sendAccountMenu(chatId, pharmacyService.isRegisteredPharmacy(chatId));
    return;
}

    if (normalizedText.equals("❓ how to use") || normalizedText.equals("❓ እንዴት እጠቀም")) {
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

    if (normalizedText.equals("📝 leave feedback") || normalizedText.equals("📝 አስተያየት ስጥ")) {
        awaitingFeedback.add(chatId);
        telegramClient.sendMessage(
                chatId,
                localizationService.text(chatId, "feedback_prompt")
        );
        return;
    }

    if (normalizedText.equals("📖 information") || normalizedText.equals("📖 መረጃ")) {
        telegramClient.sendInformationMenu(chatId);
        return;
    }

    if (normalizedText.equals("ℹ️ about tenahub") || normalizedText.equals("ℹ️ ስለ tenahub")) {
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "information_text"));
        return;
    }

    if (normalizedText.equals("📞 contacts") || normalizedText.equals("📞 አድራሻዎች")) {
        telegramClient.sendMessage(chatId, buildContactsCard(chatId));
        return;
    }

    if (normalizedText.equals("🌐 language") || normalizedText.equals("language")
            || normalizedText.equals("ቋንቋ") || normalizedText.equals("🌐 ቋንቋ")) {
        telegramClient.sendLanguageChooserMenu(chatId);
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
            restoreReservationExitKeyboard(chatId);
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

    if (normalizedText.equals("/miniappsearch")) {
        telegramClient.sendMiniAppSearchPrompt(chatId);
        return;
    }

    if (normalizedText.equals("📜 reservation history")) {
        telegramClient.sendMessage(chatId, reservationService.viewReservationHistory(chatId));
        return;
    }

    if (normalizedText.equals("🏥 register pharmacy") || normalizedText.equals("🏥 ፋርማሲ መዝግብ")) {
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

    if (normalizedText.equals("📍 share location") || normalizedText.equals("📍 አካባቢ አጋራ")) {
        telegramClient.sendLocationChoiceMenu(chatId);
        return;
    }

    if (normalizedText.equals("🗺 select ethiopia region")) {
        LocationSelectionSessionManager.start(chatId, LocationFlowType.USER_SEARCH);
        telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
        return;
    }

if (normalizedText.equals("📦 my reservations") || normalizedText.equals("📦 የእኔ ቦታ ማስያዣዎች")) {
    List<MedicineReservation> reservations = reservationService.getUserReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "no_reservations_found"), "HTML");
        return;
    }

    telegramClient.sendMyReservationsSectionMenu(
            chatId,
            localizationService.text(chatId, "my_reservations_menu_title")
    );

    return;
}

if (normalizedText.equals("⏳ pending")) {
    sendUserReservationSection(chatId, MedicineReservationStatus.PENDING, "pending_section_title");
    return;
}

if (normalizedText.equals("📦 fulfilled")) {
    sendUserReservationSection(chatId, MedicineReservationStatus.FULFILLED, "fulfilled_section_title");
    return;
}

if (normalizedText.equals("⌛ expired")) {
    sendUserReservationSection(chatId, MedicineReservationStatus.EXPIRED, "expired_section_title");
    return;
}

if (normalizedText.equals("❌ cancelled")) {
    sendUserReservationSection(chatId, MedicineReservationStatus.CANCELLED, "cancelled_section_title");
    return;
}

if (normalizedText.equals("🔁 reserve again (latest)")) {
    List<MedicineReservation> reservations = reservationService.getUserReservations(chatId);
    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "no_reservations_found"), "HTML");
        return;
    }

    MedicineReservation latest = reservations.stream()
            .max((a, b) -> {
                if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                }
                return Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId());
            })
            .orElse(null);

    if (latest == null || latest.getId() == null) {
        telegramClient.sendMessage(chatId, "⚠️ No reservation available for Reserve Again.");
        return;
    }

    runReserveAgainFlow(chatId, null, null, latest.getId());
    return;
}
    // ---------------- ADMIN MENU ----------------
    if (normalizedText.equals("🆕 pending registrations")) {
        clearAdminPharmacyManagementState(chatId);

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
        clearAdminPharmacyManagementState(chatId);

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

    if (normalizedText.equals("📄 license compliance") || normalizedText.equals("📄 license action center")) {
        clearAdminPharmacyManagementState(chatId);
        sendAdminLicenseComplianceSummary(chatId);
        return;
    }

    if (normalizedText.equals("🧾 audit trail")) {
        clearAdminPharmacyManagementState(chatId);
        sendAdminAuditTrailMenu(chatId);
        return;
    }

    if (normalizedText.equals("🏥 pharmacy management")) {
        clearAdminPharmacyManagementState(chatId);
        AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("MENU", null));
        telegramClient.sendAdminPharmacyManagementMenu(chatId);
        return;
    }

    if (normalizedText.equals("📋 all pharmacies")) {
        clearCurrentAdminPharmacyDetail(chatId);
        AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("LIST", null));
        sendAdminPharmacyManagementPageForSession(chatId, 0);
        return;
    }

    if (normalizedText.equals("🔎 search by name")) {
        clearCurrentAdminPharmacyDetail(chatId);
        AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("SEARCH_NAME", null));
        telegramClient.sendMessage(chatId, "🔎 Send pharmacy name to search.");
        return;
    }

    if (normalizedText.equals("📞 search by phone")) {
        clearCurrentAdminPharmacyDetail(chatId);
        AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("SEARCH_PHONE", null));
        telegramClient.sendMessage(chatId, "📞 Send phone number or part of the phone number.");
        return;
    }

    if (normalizedText.equals("🆔 search by telegram id")) {
        clearCurrentAdminPharmacyDetail(chatId);
        AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("SEARCH_TELEGRAM", null));
        telegramClient.sendMessage(chatId, "🆔 Send full or partial Telegram ID text to search.");
        return;
    }

    if (normalizedText.equals("📦 reservation oversight")) {
        clearAdminPharmacyManagementState(chatId);
        telegramClient.sendAdminReservationOversight(
                chatId,
                adminService.viewDetailedReservationOversight()
        );
        return;
    }

    if (normalizedText.equals("📥 feedback & issues")) {
        clearAdminPharmacyManagementState(chatId);
        adminInboxPendingAction.remove(chatId);
        adminInboxCurrentItemId.remove(chatId);
        sendAdminInboxSummary(chatId);
        return;
    }

    if (chatId.equals(ADMIN_CHAT_ID)
            && handleAdminInboxTextAction(chatId, normalizedText, originalText)) {
        return;
    }

    if (chatId.equals(ADMIN_CHAT_ID)
            && handleAdminLicenseComplianceTextAction(chatId, normalizedText, originalText)) {
        return;
    }

    if (chatId.equals(ADMIN_CHAT_ID)
            && handleAdminAuditTrailTextAction(chatId, normalizedText)) {
        return;
    }

    if (normalizedText.equals("📊 system summary")) {
        clearAdminPharmacyManagementState(chatId);
        telegramClient.sendAdminSystemSummary(
                chatId,
                adminService.viewDetailedSystemSummary()
        );
        return;
    }

    if (chatId.equals(ADMIN_CHAT_ID) && AdminPharmacyManagementSessionManager.exists(chatId)) {
        AdminPharmacyManagementSession session = AdminPharmacyManagementSessionManager.get(chatId);
        if (session != null && session.getMode() != null && session.getMode().startsWith("PRESCRIPTION_PICK:")) {
            handleAdminPrescriptionMedicineSelection(chatId, session, originalText);
            return;
        }
        if (session != null && session.isAwaitingQuery()) {
            session.setQuery(originalText == null ? "" : originalText.trim());
            AdminPharmacyManagementSessionManager.save(chatId, session);
            sendAdminPharmacyManagementPageForSession(chatId, 0);
            return;
        }
    }

    if (chatId.equals(ADMIN_CHAT_ID) && AdminPharmacyEditSessionManager.exists(chatId)) {
        AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
        if (editSession != null && originalText != null) {
            String value = originalText.trim();

            if (editSession.getField() == AdminPharmacyEditField.NAME) {
                if (value.isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Pharmacy name cannot be empty.");
                    return;
                }

                adminService.updatePharmacyName(editSession.getPharmacyId(), value);
                recordAudit(chatId, "PHARMACY_EDITED", "PHARMACY", editSession.getPharmacyId(),
                    "Updated pharmacy name", null, value);
                telegramClient.sendMessage(chatId, "✅ Pharmacy name updated.");
                AdminPharmacyEditSessionManager.remove(chatId);
                refreshAdminPharmacyDetail(chatId, editSession.getPharmacyId(), null);
                return;
            }

            if (editSession.getField() == AdminPharmacyEditField.PHONE) {
                String phone = value.replaceAll("\\s+", "");
                if (!phone.matches("^\\+?[0-9]{7,15}$")) {
                    telegramClient.sendMessage(chatId, "⚠️ Invalid phone number. Example: 0912345678");
                    return;
                }

                adminService.updatePharmacyPhone(editSession.getPharmacyId(), phone);
                recordAudit(chatId, "PHARMACY_EDITED", "PHARMACY", editSession.getPharmacyId(),
                    "Updated pharmacy phone", null, phone);
                telegramClient.sendMessage(chatId, "✅ Pharmacy phone updated.");
                AdminPharmacyEditSessionManager.remove(chatId);
                refreshAdminPharmacyDetail(chatId, editSession.getPharmacyId(), null);
                return;
            }

            if (editSession.getField() == AdminPharmacyEditField.LANDMARK) {
                if ("-".equals(value)) {
                    value = "";
                }
                adminService.updatePharmacyLandmark(editSession.getPharmacyId(), value.isBlank() ? null : value);
                recordAudit(chatId, "PHARMACY_EDITED", "PHARMACY", editSession.getPharmacyId(),
                    "Updated pharmacy landmark", null, value.isBlank() ? "<cleared>" : value);
                telegramClient.sendMessage(chatId, "✅ Pharmacy landmark updated.");
                AdminPharmacyEditSessionManager.remove(chatId);
                refreshAdminPharmacyDetail(chatId, editSession.getPharmacyId(), null);
                return;
            }
        }
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

    if (normalizedText.equals("🧾 prescription reviews")) {
        List<MedicineReservation> reservations = reservationService.getPendingReservations(chatId).stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .filter(reservation -> reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_PENDING)
                .toList();

        if (reservations.isEmpty()) {
            telegramClient.sendMessage(chatId, "🧾 <b>Prescription Reviews</b>\n\nNo prescription reviews are pending.", "HTML");
            return;
        }

        telegramClient.sendMessage(chatId, "🧾 <b>Prescription Reviews</b>", "HTML");
        sendPrescriptionReviewCards(chatId, reservations);
        return;
    }

    if (normalizedText.equals("📷 pickup scanner")) {
        Long pharmacyTelegramId = update.getMessage() != null
                && update.getMessage().getFrom() != null
                ? update.getMessage().getFrom().getId()
                : chatId;
        telegramClient.sendPharmacyPickupScannerPrompt(chatId, pharmacyTelegramId);
        return;
    }

    if (normalizedText.equals("📊 performance")) {
        try {
            telegramClient.sendPharmacyPerformanceCard(chatId, pharmacyPerformanceService.buildPerformanceCard(chatId));
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
        return;
    }

if (normalizedText.equals("📦 pending reservations")) {
    List<MedicineReservation> reservations = reservationService.getPendingReservations(chatId);

    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, "📦 <b>Pending Reservations</b>\n\nNo pending reservations.", "HTML");
        return;
    }

    telegramClient.sendMessage(chatId, "📦 <b>Pending Reservations</b>", "HTML");

    java.util.LinkedHashMap<String, java.util.List<MedicineReservation>> pendingGroups = new java.util.LinkedHashMap<>();
    for (MedicineReservation r : reservations) {
        String key = r.getReservationGroupId() != null ? r.getReservationGroupId() : "solo_" + r.getId();
        pendingGroups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
    }
    for (java.util.Map.Entry<String, java.util.List<MedicineReservation>> entry : pendingGroups.entrySet()) {
        java.util.List<MedicineReservation> group = entry.getValue();
        if (group.size() == 1 && group.get(0).getReservationGroupId() == null) {
            MedicineReservation r = group.get(0);
            telegramClient.sendPharmacyPendingReservationCard(chatId, r.getId(), r.getUserId(),
                    r.getMedicineName(), r.getRequestedQuantity(), r.getCustomerPhone(), r.getCustomerName(), r.getQrToken(),
                    r.isPrescriptionRequired(), r.getPrescriptionReviewStatus() == null ? null : r.getPrescriptionReviewStatus().name());
        } else {
            telegramClient.sendPharmacyPendingGroupedReservationCard(chatId, entry.getKey(), group);
        }
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

    java.util.LinkedHashMap<String, java.util.List<MedicineReservation>> approvedGroups = new java.util.LinkedHashMap<>();
    for (MedicineReservation r : reservations) {
        String key = r.getReservationGroupId() != null ? r.getReservationGroupId() : "solo_" + r.getId();
        approvedGroups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
    }
    for (java.util.Map.Entry<String, java.util.List<MedicineReservation>> entry : approvedGroups.entrySet()) {
        java.util.List<MedicineReservation> group = entry.getValue();
        if (group.size() == 1 && group.get(0).getReservationGroupId() == null) {
            MedicineReservation r = group.get(0);
            String holdUntil = r.getExpiresAt() == null ? null : formatter.format(r.getExpiresAt());
            telegramClient.sendPharmacyApprovedReservationCard(chatId, r.getId(), r.getUserId(),
                    r.getMedicineName(), r.getRequestedQuantity(), r.getCustomerPhone(), r.getCustomerName(), holdUntil, r.getQrToken());
        } else {
            MedicineReservation first = group.get(0);
            String holdUntil = first.getExpiresAt() == null ? null : formatter.format(first.getExpiresAt());
            telegramClient.sendPharmacyApprovedGroupedReservationCard(chatId, entry.getKey(), group, holdUntil);
        }
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

    java.util.LinkedHashMap<String, java.util.List<MedicineReservation>> fulfillGroups = new java.util.LinkedHashMap<>();
    for (MedicineReservation r : reservations) {
        String key = r.getReservationGroupId() != null ? r.getReservationGroupId() : "solo_" + r.getId();
        fulfillGroups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
    }
    for (java.util.Map.Entry<String, java.util.List<MedicineReservation>> entry : fulfillGroups.entrySet()) {
        java.util.List<MedicineReservation> group = entry.getValue();
        if (group.size() == 1 && group.get(0).getReservationGroupId() == null) {
            MedicineReservation r = group.get(0);
            String holdUntil = r.getExpiresAt() == null ? null : formatter.format(r.getExpiresAt());
            telegramClient.sendPharmacyApprovedReservationCard(chatId, r.getId(), r.getUserId(),
                    r.getMedicineName(), r.getRequestedQuantity(), r.getCustomerPhone(), r.getCustomerName(), holdUntil, r.getQrToken());
        } else {
            MedicineReservation first = group.get(0);
            String holdUntil = first.getExpiresAt() == null ? null : formatter.format(first.getExpiresAt());
            telegramClient.sendPharmacyApprovedGroupedReservationCard(chatId, entry.getKey(), group, holdUntil);
        }
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

    if (normalizedText.equals("🖼 manage photos") || normalizedText.equals("🖼 update pharmacy photo")) {
        telegramClient.sendPharmacyPhotoManagementMenu(chatId);
        return;
    }

    if (normalizedText.equals("💊 manage medicine photos")) {
        telegramClient.sendMedicinePhotoManagementMenu(chatId);
        return;
    }

    if (normalizedText.equals("➕ add medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "add");
        return;
    }

    if (normalizedText.equals("👁 view medicine photos")) {
        startMedicinePhotoSelectionFlow(chatId, "view");
        return;
    }

    if (normalizedText.equals("⭐ set main medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "set_main");
        return;
    }

    if (normalizedText.equals("🗑 remove medicine photo")) {
        startMedicinePhotoSelectionFlow(chatId, "remove");
        return;
    }

    if (normalizedText.equals("🛑 temporary close")
            || normalizedText.equals("🛑 temporary close (24h)")
            || normalizedText.equals("⏳ temporary close (custom)")) {
        telegramClient.sendTemporaryCloseDurationPicker(chatId);
        return;
    }

    if (normalizedText.equals("✅ reopen now")) {
        try {
            pharmacyService.clearTemporaryClosure(chatId);
            telegramClient.sendMessage(
                    chatId,
                    "✅ Pharmacy reopened. Reservations are enabled again."
            );
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
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
    if (normalizedText.equals("📦 bulk inventory update")) {
        telegramClient.sendBulkInventoryUpdateInstructions(chatId);
        UpdateSessionManager.start(chatId, UpdateField.INVENTORY_BULK);
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

    if (normalizedText.equals("🧾 prescription settings")) {
        startInventoryPrescriptionSelectionFlow(chatId);
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

    if (normalizedText.equals("💡 restock suggestions")) {
        telegramClient.sendMessage(chatId, inventoryService.getAdvancedRestockSuggestions(chatId));
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
            if (session.getField() == UpdateField.LICENSE && session.isWaitingForLicenseExpiryDate()) {
                session.setWaitingForLicenseExpiryDate(false);
                session.setPendingFileId(null);
                telegramClient.sendMessage(chatId, "📄 Please upload the new license photo");
                return;
            }

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

        if (session.getField() == UpdateField.LICENSE && session.isWaitingForLicenseExpiryDate()) {
            LocalDate expiryDate = parseFutureOrTodayDate(originalText);
            if (expiryDate == null) {
                telegramClient.sendMessage(chatId, localizationService.text(chatId, "license_expiry_invalid"), "HTML");
                return;
            }

            try {
                String pendingFileId = session.getPendingFileId();
                if (pendingFileId == null || pendingFileId.isBlank()) {
                    telegramClient.sendMessage(chatId, "📄 Please upload the new license photo");
                    session.setWaitingForLicenseExpiryDate(false);
                    return;
                }

                pharmacyService.savePendingLicenseUpdate(chatId, pendingFileId, expiryDate);

                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                String caption = "🔄 <b>License Update Request</b>\n\n"
                        + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                    + "🏙️ <b>City:</b> " + displayLocation(ADMIN_CHAT_ID, pharmacy.getCity()) + "\n"
                    + "📍 <b>Area:</b> " + displayLocation(ADMIN_CHAT_ID, pharmacy.getArea()) + "\n"
                        + "📞 <b>Phone:</b> " + pharmacy.getPhone() + "\n"
                        + "💊 <b>Medicines:</b> " + pharmacy.getMedicines() + "\n"
                        + "🕒 <b>Open:</b> " + pharmacy.getOpenTime() + "\n"
                        + "🌙 <b>Close:</b> " + pharmacy.getCloseTime() + "\n"
                        + "📅 <b>License Expiry:</b> " + expiryDate + "\n"
                        + "📌 <b>Latitude:</b> " + pharmacy.getLatitude() + "\n"
                        + "📌 <b>Longitude:</b> " + pharmacy.getLongitude() + "\n"
                        + "🆔 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                telegramClient.sendPhotoWithLicenseUpdateButtons(
                        ADMIN_CHAT_ID,
                        pendingFileId,
                        caption,
                        chatId
                );

                telegramClient.sendMessage(
                        chatId,
                        localizationService.text(chatId, "license_update_received_pending")
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

            case PHOTO_ADD -> {
                telegramClient.sendMessage(chatId, "Send one photo now. You can upload up to 4 photos.");
                return;
            }

            case PHOTO_SET_MAIN -> {
                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
                pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
                List<com.tenahub.bot.entity.PharmacyPhoto> photos = pharmacyPhotoService.listByPharmacyId(pharmacy.getId());

                if (photos.isEmpty()) {
                    telegramClient.sendMessage(chatId, "No photos found.");
                    UpdateSessionManager.remove(chatId);
                    telegramClient.sendPharmacyPhotoManagementMenu(chatId);
                    return;
                }

                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                if (idx < 1 || idx > photos.size()) {
                    telegramClient.sendMessage(chatId, "⚠️ Number out of range.");
                    return;
                }

                pharmacyPhotoService.setMainPhoto(pharmacy.getId(), photos.get(idx - 1).getId());
                telegramClient.sendMessage(chatId, "✅ Main photo updated.");
                UpdateSessionManager.remove(chatId);
                telegramClient.sendPharmacyPhotoManagementMenu(chatId);
                return;
            }

            case PHOTO_REMOVE -> {
                Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
                pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
                List<com.tenahub.bot.entity.PharmacyPhoto> photos = pharmacyPhotoService.listByPharmacyId(pharmacy.getId());

                if (photos.isEmpty()) {
                    telegramClient.sendMessage(chatId, "No photos found.");
                    UpdateSessionManager.remove(chatId);
                    telegramClient.sendPharmacyPhotoManagementMenu(chatId);
                    return;
                }

                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                if (idx < 1 || idx > photos.size()) {
                    telegramClient.sendMessage(chatId, "⚠️ Number out of range.");
                    return;
                }

                pharmacyPhotoService.removePhoto(pharmacy.getId(), photos.get(idx - 1).getId());
                int left = pharmacyPhotoService.listByPharmacyId(pharmacy.getId()).size();
                telegramClient.sendMessage(chatId, "✅ Photo removed (" + left + "/" + PharmacyPhotoService.MAX_PHOTOS + ").");
                UpdateSessionManager.remove(chatId);
                telegramClient.sendPharmacyPhotoManagementMenu(chatId);
                return;
            }

            case MEDICINE_PHOTO_SELECT -> {
                String action = medicinePhotoPendingAction.get(chatId);
                if (action == null || action.isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ No medicine photo action selected. Start again.");
                    UpdateSessionManager.remove(chatId);
                    telegramClient.sendMedicinePhotoManagementMenu(chatId);
                    return;
                }

                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                PharmacyInventory selected = requireOwnedMedicineByIndex(chatId, idx);
                medicinePhotoPendingMedicineId.put(chatId, selected.getId());
                medicinePhotoPendingAction.remove(chatId);
                UpdateSessionManager.remove(chatId);

                if ("add".equals(action)) {
                    telegramClient.sendMessage(chatId,
                            "Send one photo now for " + displayMedicine(chatId, selected.getMedicineName())
                                    + ". You can upload up to " + MedicinePhotoService.MAX_PHOTOS + " photos.");
                    UpdateSessionManager.start(chatId, UpdateField.MEDICINE_PHOTO_ADD);
                    return;
                }

                if ("view".equals(action)) {
                    showMedicineOwnPhotos(chatId, selected.getId());
                    return;
                }

                if ("set_main".equals(action)) {
                    startSetMainMedicinePhotoFlow(chatId, selected.getId());
                    return;
                }

                if ("remove".equals(action)) {
                    startRemoveMedicinePhotoFlow(chatId, selected.getId());
                    return;
                }

                telegramClient.sendMessage(chatId, "⚠️ Unknown medicine photo action.");
                telegramClient.sendMedicinePhotoManagementMenu(chatId);
                return;
            }

            case MEDICINE_PHOTO_ADD -> {
                telegramClient.sendMessage(chatId, "Send one photo now for the selected medicine.");
                return;
            }

            case MEDICINE_PHOTO_SET_MAIN -> {
                Long medicineId = medicinePhotoPendingMedicineId.get(chatId);
                if (medicineId == null) {
                    telegramClient.sendMessage(chatId, "⚠️ No medicine selected. Start again.");
                    UpdateSessionManager.remove(chatId);
                    telegramClient.sendMedicinePhotoManagementMenu(chatId);
                    return;
                }

                PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
                List<com.tenahub.bot.entity.MedicinePhoto> photos = medicinePhotoService.listByMedicineId(medicine.getId());

                if (photos.isEmpty()) {
                    telegramClient.sendMessage(chatId, "No medicine photos found.");
                    UpdateSessionManager.remove(chatId);
                    medicinePhotoPendingMedicineId.remove(chatId);
                    telegramClient.sendMedicinePhotoManagementMenu(chatId);
                    return;
                }

                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                if (idx < 1 || idx > photos.size()) {
                    telegramClient.sendMessage(chatId, "⚠️ Number out of range.");
                    return;
                }

                medicinePhotoService.setMainPhoto(medicine.getId(), photos.get(idx - 1).getId());
                telegramClient.sendMessage(chatId,
                        "✅ Main medicine photo updated for " + displayMedicine(chatId, medicine.getMedicineName()) + ".");
                UpdateSessionManager.remove(chatId);
                medicinePhotoPendingMedicineId.remove(chatId);
                telegramClient.sendMedicinePhotoManagementMenu(chatId);
                return;
            }

            case MEDICINE_PHOTO_REMOVE -> {
                Long medicineId = medicinePhotoPendingMedicineId.get(chatId);
                if (medicineId == null) {
                    telegramClient.sendMessage(chatId, "⚠️ No medicine selected. Start again.");
                    UpdateSessionManager.remove(chatId);
                    telegramClient.sendMedicinePhotoManagementMenu(chatId);
                    return;
                }

                PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
                List<com.tenahub.bot.entity.MedicinePhoto> photos = medicinePhotoService.listByMedicineId(medicine.getId());

                if (photos.isEmpty()) {
                    telegramClient.sendMessage(chatId, "No medicine photos found.");
                    UpdateSessionManager.remove(chatId);
                    medicinePhotoPendingMedicineId.remove(chatId);
                    telegramClient.sendMedicinePhotoManagementMenu(chatId);
                    return;
                }

                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                if (idx < 1 || idx > photos.size()) {
                    telegramClient.sendMessage(chatId, "⚠️ Number out of range.");
                    return;
                }

                medicinePhotoService.removePhoto(medicine.getId(), photos.get(idx - 1).getId());
                int left = medicinePhotoService.listByMedicineId(medicine.getId()).size();
                telegramClient.sendMessage(chatId,
                        "✅ Medicine photo removed (" + left + "/" + MedicinePhotoService.MAX_PHOTOS + ") for "
                                + displayMedicine(chatId, medicine.getMedicineName()) + ".");
                UpdateSessionManager.remove(chatId);
                medicinePhotoPendingMedicineId.remove(chatId);
                telegramClient.sendMedicinePhotoManagementMenu(chatId);
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

            case INVENTORY_BULK -> {
                InventoryService.BulkInventoryUpdateResult result =
                        inventoryService.bulkUpsertFromText(chatId, originalText);

                telegramClient.sendBulkInventoryUpdateResult(chatId, result);

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
            case INVENTORY_PRESCRIPTION_SELECT -> {
                Integer idx;
                try {
                    idx = Integer.parseInt(originalText.trim());
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                    return;
                }

                PharmacyInventory selected = requireOwnedMedicineByIndex(chatId, idx);
                telegramClient.sendPharmacyPrescriptionSettingCard(
                        chatId,
                        selected.getId(),
                        selected.getMedicineName(),
                        selected.getQuantity(),
                        selected.getPrice(),
                        selected.getCurrency(),
                        selected.isRequiresPrescription(),
                        false,
                        selected.getPharmacyId()
                );
                UpdateSessionManager.remove(chatId);
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
case TEMP_CLOSE -> {
    // UI flow: duration selected with buttons, then user types custom reason
    if (session.getTempHour() != null) {
        String reason = originalText == null ? "" : originalText.trim();
        if (reason.isBlank()) {
            telegramClient.sendMessage(chatId, "⚠️ Please send a custom reason text.");
            return;
        }

        int durationHours = session.getTempHour();
        pharmacyService.setTemporaryClosure(chatId, reason, durationHours);

        telegramClient.sendMessage(
                chatId,
                "🛑 Pharmacy temporarily closed for " + durationHours + " hour(s).\n" +
                        "Reason: " + reason + "\n\n" +
                        "Use ✅ Reopen Now anytime to open again."
        );

        UpdateSessionManager.remove(chatId);
        return;
    }

    // Backward compatibility for typed format: hours | reason
    String[] parts = originalText.split("\\|", 2);

    if (parts.length < 2) {
        telegramClient.sendMessage(
                chatId,
                "⚠️ Format: hours | reason\n\nExample:\n8 | Power outage"
        );
        return;
    }

    int durationHours;
    try {
        durationHours = Integer.parseInt(parts[0].trim());
    } catch (Exception e) {
        telegramClient.sendMessage(chatId, "⚠️ Hours must be a number.");
        return;
    }

    if (durationHours <= 0 || durationHours > 168) {
        telegramClient.sendMessage(chatId, "⚠️ Hours must be between 1 and 168.");
        return;
    }

    String reason = parts[1].trim();
    if (reason.isBlank()) {
        telegramClient.sendMessage(chatId, "⚠️ Please provide a closure reason after '|'.");
        return;
    }

    pharmacyService.setTemporaryClosure(chatId, reason, durationHours);

    telegramClient.sendMessage(
            chatId,
            "🛑 Pharmacy temporarily closed for " + durationHours + " hour(s).\n" +
                    "Reason: " + reason + "\n\n" +
                    "Use ✅ Reopen Now anytime to open again."
    );

    UpdateSessionManager.remove(chatId);
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

            ReservationSessionManager.remove(chatId);
            restoreReservationExitKeyboard(chatId);
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
            formatVerifiedPharmacyName(pharmacy),
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

            // If editing name from summary, show updated summary
            if (session.isEditingName()) {
                session.setEditingName(false);

                Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                PharmacyInventory inventory = inventoryRepository
                        .findByPharmacyIdAndMedicineNameIgnoreCase(session.getPharmacyId(), session.getMedicineName())
                        .orElse(null);

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

                Integer sourceMessageId = session.getSourceMessageId();
                if (sourceMessageId != null) {
                    telegramClient.sendReservationSummary(
                            chatId,
                            sourceMessageId,
                            session.getMedicineName(),
                            session.getQuantity(),
                            session.getCustomerName(),
                            session.getCustomerPhone(),
                            formatVerifiedPharmacyName(pharmacy),
                            pharmacy.getPhone(),
                            distance,
                            pharmacy.getLatitude(),
                            pharmacy.getLongitude(),
                            session.getPharmacyId()
                    );
                } else {
                    telegramClient.sendReservationSummary(
                            chatId,
                            null,
                            session.getMedicineName(),
                            session.getQuantity(),
                            session.getCustomerName(),
                            session.getCustomerPhone(),
                            formatVerifiedPharmacyName(pharmacy),
                            pharmacy.getPhone(),
                            distance,
                            pharmacy.getLatitude(),
                            pharmacy.getLongitude(),
                            session.getPharmacyId()
                    );
                }
                return;
            }

            // Normal flow: proceed to phone input
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

            // Store phone in session
            session.setCustomerPhone(phone);
            session.setWaitingForPhone(false);

            // If editing phone from summary, show updated summary
            if (session.isEditingPhone()) {
                session.setEditingPhone(false);

                Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

                PharmacyInventory inventory = inventoryRepository
                        .findByPharmacyIdAndMedicineNameIgnoreCase(session.getPharmacyId(), session.getMedicineName())
                        .orElse(null);

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

                Integer sourceMessageId = session.getSourceMessageId();
                if (sourceMessageId != null) {
                    telegramClient.sendReservationSummary(
                            chatId,
                            sourceMessageId,
                            session.getMedicineName(),
                            session.getQuantity(),
                            session.getCustomerName(),
                            phone,
                            formatVerifiedPharmacyName(pharmacy),
                            pharmacy.getPhone(),
                            distance,
                            pharmacy.getLatitude(),
                            pharmacy.getLongitude(),
                            session.getPharmacyId()
                    );
                } else {
                    telegramClient.sendReservationSummary(
                            chatId,
                            null,
                            session.getMedicineName(),
                            session.getQuantity(),
                            session.getCustomerName(),
                            phone,
                            formatVerifiedPharmacyName(pharmacy),
                            pharmacy.getPhone(),
                            distance,
                            pharmacy.getLatitude(),
                            pharmacy.getLongitude(),
                            session.getPharmacyId()
                    );
                }

                telegramClient.sendMessageRemoveKeyboard(chatId, " ");
                return;
            }

            // Normal flow: show summary for confirmation
            Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            PharmacyInventory inventory = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(session.getPharmacyId(), session.getMedicineName())
                    .orElse(null);

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

            // Show reservation summary with confirm/edit options
            Integer sourceMessageId = session.getSourceMessageId();
            if (sourceMessageId != null) {
                telegramClient.sendReservationSummary(
                        chatId,
                        sourceMessageId,
                        session.getMedicineName(),
                        session.getQuantity(),
                        session.getCustomerName(),
                        phone,
                        formatVerifiedPharmacyName(pharmacy),
                        pharmacy.getPhone(),
                        distance,
                        pharmacy.getLatitude(),
                        pharmacy.getLongitude(),
                        session.getPharmacyId()
                );
            } else {
                telegramClient.sendReservationSummary(
                        chatId,
                        null,
                        session.getMedicineName(),
                        session.getQuantity(),
                        session.getCustomerName(),
                        phone,
                        formatVerifiedPharmacyName(pharmacy),
                        pharmacy.getPhone(),
                        distance,
                        pharmacy.getLatitude(),
                        pharmacy.getLongitude(),
                        session.getPharmacyId()
                );
            }

            telegramClient.sendMessageRemoveKeyboard(chatId, " ");
            return;
        }
    }

    // -------- MULTI RESERVATION FLOW --------
    if (MultiReservationSessionManager.exists(chatId)) {
        MultiReservationSession session = MultiReservationSessionManager.get(chatId);

        if (normalizedText.equals(MAIN_HOME)) {
            MultiReservationSessionManager.remove(chatId);
            telegramClient.sendUserDashboard(chatId);
            return;
        }

        if (normalizedText.equals(BACK_ARROW) || normalizedText.equals(BACK_SYMBOL)) {
            if (session.isWaitingForPhone()) {
                session.setWaitingForPhone(false);
                session.setWaitingForName(true);
                telegramClient.sendMessage(chatId, "👤 Please enter your full name.\n\nExample:\nTeketsel Beyene");
                return;
            }

            if (session.isWaitingForName()) {
                session.setWaitingForName(false);
                telegramClient.sendMultiReserveMedicineQuantityPicker(chatId, session.getMatchedMedicines());
                return;
            }

            MultiReservationSessionManager.remove(chatId);
            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                MultiMedicineSearchSession multiSession = MultiMedicineSearchSessionManager.get(chatId);
                telegramClient.sendMultiMedicinePanel(chatId, multiSession.getSelectedMedicines());
            } else {
                telegramClient.sendUserDashboard(chatId);
            }
            return;
        }

        if (session.isWaitingForName()) {
            String fullName = originalText.trim();

            if (fullName.isBlank() || fullName.length() < 3) {
                telegramClient.sendMessage(chatId, "⚠️ Please enter a valid full name.\n\nExample:\nTeketsel Beyene");
                return;
            }

            session.setCustomerName(fullName);
            session.setWaitingForName(false);
            session.setWaitingForPhone(true);

            telegramClient.sendMessage(chatId, "📱 Now please enter your phone number.\n\nExample:\n+251912345678");
            return;
        }

        if (session.isWaitingForPhone()) {
            String phone = originalText.trim();

            if (phone.isBlank() || phone.length() < 9) {
                telegramClient.sendMessage(chatId, "⚠️ Please enter a valid phone number.\n\nExample:\n+251912345678");
                return;
            }

            session.setCustomerPhone(phone);
            session.setWaitingForPhone(false);

            telegramClient.sendMultiReserveFinalConfirmation(chatId, session.getCustomerName(), session.getCustomerPhone(), session.getMedicineQuantities());
            return;
        }
    }

    // -------- COMMANDS -------- 
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
            var reservation = reservationService.fulfillReservationAndNotify(reservationId);

            telegramClient.sendMessage(
                    chatId,
                    "✅ Reservation marked fulfilled.\n\n"
                            + "🆔 " + reservation.getId() + "\n"
                            + "💊 " + reservation.getMedicineName() + "\n"
                            + "🔢 Qty: " + reservation.getRequestedQuantity()
            );
            restoreKeyboard(reservation.getUserId());
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

logSearchIfCatalogMatch(chatId, medicine);

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

sendNoResultWithTypoSuggestion(chatId, medicine);
}







private boolean handleSharedLocationSelectionText(Long chatId, String originalText) {
    if (!LocationSelectionSessionManager.exists(chatId)) {
        return false;
    }

    LocationSelectionSession session = LocationSelectionSessionManager.get(chatId);

    if (originalText == null) {
        return true;
    }

   if (originalText.equalsIgnoreCase("🏠 Main") || "🏠 ዋናው ገጽ".equals(originalText)) {
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

    String canonicalLocationInput = telegramClient.canonicalLocationValue(originalText);

    if (originalText.equalsIgnoreCase(localizationService.text(chatId, "btn_cancel"))) {
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

        if (session.getFlowType() == LocationFlowType.ADMIN_EDIT_PHARMACY_LOCATION) {
            AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
            if (editSession != null) {
                telegramClient.sendAdminPharmacyEditLocationModeMenu(chatId, editSession.getPharmacyId());
                return true;
            }
        }

        restoreKeyboard(chatId);
        return true;
    }

    if (originalText.equalsIgnoreCase(localizationService.text(chatId, "btn_back"))) {
        handleSharedLocationBack(chatId, session);
        return true;
    }

    if (session.isWaitingRegion()) {
        String matchedRegion = EthiopiaLocationCatalog.getRegions().stream()
                .filter(r -> r.equalsIgnoreCase(canonicalLocationInput))
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
            if (!EthiopiaLocationCatalog.isAddisAbabaCity(canonicalLocationInput)) {
                telegramClient.sendMessage(chatId, "⚠️ Please choose Addis Ababa using the button.");
                return true;
            }

            session.setSelectedCity("Addis Ababa");
            session.setSubCityMode();
            telegramClient.sendAddisAbabaSubCityKeyboard(chatId);
            return true;
        }

        String matchedCity = EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion()).stream()
        .filter(c -> c.equalsIgnoreCase(canonicalLocationInput))
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
                .filter(s -> s.equalsIgnoreCase(canonicalLocationInput))
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
                .filter(a -> a.equalsIgnoreCase(canonicalLocationInput))
                    .findFirst()
                    .orElse(null);
        } else {
            matchedArea = EthiopiaLocationCatalog.getAreasByRegionAndCity(
                    session.getSelectedRegion(),
                    session.getSelectedCity()
            ).stream()
                .filter(a -> a.equalsIgnoreCase(canonicalLocationInput))
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

private void handleSharedLocationBack(Long chatId, LocationSelectionSession session) {
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
            case ADMIN_EDIT_PHARMACY_LOCATION -> {
                AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
                if (editSession != null) {
                    telegramClient.sendAdminPharmacyEditLocationModeMenu(chatId, editSession.getPharmacyId());
                } else {
                    restoreKeyboard(chatId);
                }
            }
        }
    }
}
private void completeSharedLocationSelection(Long chatId, LocationSelectionSession session) {
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
                "Region: " + displayLocation(chatId, option.getRegion()) + "\n" +
                "City: " + displayLocation(chatId, option.getCity()) + "\n" +
                    (session.getSelectedSubCity() != null
                    ? "Sub-city: " + displayLocation(chatId, session.getSelectedSubCity()) + "\n"
                            : "") +
                "Area: " + displayLocation(chatId, option.getArea()) + "\n\n" +
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
                "Region: " + displayLocation(chatId, option.getRegion()) + "\n" +
                "City: " + displayLocation(chatId, option.getCity()) + "\n" +
                    (session.getSelectedSubCity() != null
                    ? "Sub-city: " + displayLocation(chatId, session.getSelectedSubCity()) + "\n"
                            : "") +
                "Area: " + displayLocation(chatId, option.getArea()) + "\n\n" +
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

    telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());
    finalizeRegistrationLocation(chatId, regSession);
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
        case ADMIN_EDIT_PHARMACY_LOCATION -> {
            AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
            if (editSession == null) {
                telegramClient.sendMessage(chatId, "⚠️ Admin edit session not found.");
                LocationSelectionSessionManager.remove(chatId);
                return;
            }

            adminService.updatePharmacyLocation(
                    editSession.getPharmacyId(),
                    option.getLatitude(),
                    option.getLongitude(),
                    option.getCity(),
                    option.getArea()
            );

            telegramClient.sendLocation(chatId, option.getLatitude(), option.getLongitude());
            telegramClient.sendMessage(chatId, "✅ Pharmacy structured location updated.");
            AdminPharmacyEditSessionManager.remove(chatId);
            refreshAdminPharmacyDetail(chatId, editSession.getPharmacyId(), null);
        }
    }

    LocationSelectionSessionManager.remove(chatId);
}

    private String buildInventoryView(List<PharmacyInventory> inventory) {

    if (inventory == null || inventory.isEmpty()) {
        return "📦 <b>Your Inventory</b>\n\nInventory is empty.";
    }

    StringBuilder inStock = new StringBuilder();
    StringBuilder lowStock = new StringBuilder();
    StringBuilder outOfStock = new StringBuilder();

    for (PharmacyInventory item : inventory) {
        String medicine = item.getMedicineName();
        Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();
                String prescriptionText = item.isRequiresPrescription() ? "Prescription required: Yes" : "Prescription required: No";

        String priceText = item.getPrice() != null
                ? item.getPrice().toPlainString() + " " +
                  (item.getCurrency() == null || item.getCurrency().isBlank() ? "ETB" : item.getCurrency())
                : "No price";

        if (item.isOutOfStock() || qty <= 0) {
            outOfStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(priceText)
                    .append(" — ")
                    .append(prescriptionText)
                    .append("\n");
        } else if (qty <= 10) {
            lowStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(qty).append(" left")
                    .append(" — ")
                    .append(priceText)
                    .append(" — ")
                    .append(prescriptionText)
                    .append("\n");
        } else {
            inStock.append("💊 ")
                    .append(medicine)
                    .append(" — ")
                    .append(qty).append(" left")
                    .append(" — ")
                    .append(priceText)
                    .append(" — ")
                    .append(prescriptionText)
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
private String buildUserAccountView(Long chatId) {
    StringBuilder sb = new StringBuilder();

    UserLocation location = userLocationService.getLocation(chatId);
    boolean isRegisteredPharmacy = pharmacyService.isRegisteredPharmacy(chatId);

    sb.append(localizationService.text(chatId, "account_overview_title")).append("\n\n");
    sb.append(localizationService.text(chatId, "telegram_id_label", chatId)).append("\n");

    if (location != null) {
        String locationLabel;

        if (location.getDisplayName() != null && !location.getDisplayName().isBlank()) {
            locationLabel = displayLocationAddress(chatId, location.getDisplayName());
        } else if (location.getArea() != null && !location.getArea().isBlank()
                && location.getCity() != null && !location.getCity().isBlank()) {
            locationLabel = displayLocationAddress(chatId, location.getArea() + ", " + location.getCity());
        } else if (location.getCity() != null && !location.getCity().isBlank()) {
            locationLabel = displayLocation(chatId, location.getCity());
        } else if (location.getLatitude() != null && location.getLongitude() != null) {
            locationLabel = location.getLatitude() + ", " + location.getLongitude();
        } else {
            locationLabel = localizationService.text(chatId, "saved_location_ok");
        }

        sb.append(localizationService.text(chatId, "saved_location_label", locationLabel)).append("\n");
    } else {
        sb.append(localizationService.text(chatId, "saved_location_label",
                localizationService.text(chatId, "saved_location_missing"))).append("\n");
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

    sb.append("\n").append(localizationService.text(chatId, "reservations_summary_title")).append("\n")
      .append(localizationService.text(chatId, "reservation_count_line",
              localizationService.text(chatId, "res_status_pending"), pendingCount)).append("\n")
      .append(localizationService.text(chatId, "reservation_count_line",
              localizationService.text(chatId, "res_status_approved"), approvedCount)).append("\n")
      .append(localizationService.text(chatId, "reservation_count_line",
              localizationService.text(chatId, "res_status_fulfilled"), fulfilledCount)).append("\n")
      .append(localizationService.text(chatId, "reservation_count_line",
              localizationService.text(chatId, "res_status_cancelled"), cancelledCount)).append("\n")
      .append(localizationService.text(chatId, "reservation_count_line",
              localizationService.text(chatId, "res_status_expired"), expiredCount)).append("\n");

    sb.append("\n").append(localizationService.text(chatId, "pharmacy_status_label",
            isRegisteredPharmacy
                    ? localizationService.text(chatId, "registered_status")
                    : localizationService.text(chatId, "not_registered_status"))).append("\n");

    if (!isRegisteredPharmacy) {
        sb.append(localizationService.text(chatId, "register_hint")).append("\n");
    }

    sb.append("\n").append(localizationService.text(chatId, "recent_reservations_title")).append("\n");

    if (allReservations.isEmpty()) {
        sb.append(localizationService.text(chatId, "no_recent_reservations")).append("\n");
    } else {
        allReservations.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(3)
                .forEach(r -> {
                    String pharmacyName = pharmacyRepository.findById(r.getPharmacyId())
                            .map(Pharmacy::getName)
                            .orElse(localizationService.text(chatId, "unknown_pharmacy"));

                    String statusLabel = switch (r.getStatus()) {
                        case PENDING -> localizationService.text(chatId, "res_status_pending");
                        case APPROVED -> localizationService.text(chatId, "res_status_approved");
                        case FULFILLED -> localizationService.text(chatId, "res_status_fulfilled");
                        case CANCELLED -> localizationService.text(chatId, "res_status_cancelled");
                        case EXPIRED -> localizationService.text(chatId, "res_status_expired");
                        case REJECTED -> localizationService.text(chatId, "res_status_rejected");
                    };

                    sb.append("• ")
                      .append(r.getMedicineName())
                      .append(" × ")
                      .append(r.getRequestedQuantity())
                      .append(" — ")
                      .append(shorten(pharmacyName, 22))
                      .append(" [")
                      .append(statusLabel)
                      .append("]\n");
                });
    }

    sb.append("\n").append(localizationService.text(chatId, "quick_actions_hint"));

    return sb.toString();
}

private String shorten(String text, int max) {
    if (text == null) return "Unknown";
    if (text.length() <= max) return text;
    return text.substring(0, max - 3) + "...";
}
private void restoreKeyboard(Long chatId) {
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

private String buildContactsCard(Long chatId) {
    String websiteText = formatWebsiteValue(infoWebsite);
    String phoneText = formatPhoneValue(infoSupportPhone);
    String emailText = formatEmailValue(infoSupportEmail);

    StringBuilder sb = new StringBuilder();
    sb.append(localizationService.text(chatId, "contacts_text_title"))
            .append("\n\n")
            .append(localizationService.text(chatId, "contacts_website_label", websiteText)).append("\n")
            .append(localizationService.text(chatId, "contacts_phone_label", phoneText)).append("\n")
            .append(localizationService.text(chatId, "contacts_email_label", emailText));

    if (hasText(infoTelegramUsername)) {
        sb.append("\n")
                .append(localizationService.text(chatId, "contacts_telegram_label", formatTelegramValue(infoTelegramUsername)));
    }

    if (hasText(infoPartnershipContact)) {
        sb.append("\n")
                .append(localizationService.text(chatId, "contacts_partnership_label", escapeHtml(infoPartnershipContact.trim())));
    }

    return sb.toString();
}

private String formatWebsiteValue(String website) {
    if (!hasText(website) || "N/A".equalsIgnoreCase(website.trim())) {
        return "N/A";
    }

    String value = website.trim();
    String href = value.startsWith("http://") || value.startsWith("https://")
            ? value
            : "https://" + value;
    return "<a href=\"" + escapeHtml(href) + "\">" + escapeHtml(value) + "</a>";
}

private String formatPhoneValue(String phone) {
    if (!hasText(phone) || "N/A".equalsIgnoreCase(phone.trim())) {
        return "N/A";
    }

    String value = phone.trim();
    String normalized = value.replaceAll("\\s+", "");
    return "<a href=\"tel:" + escapeHtml(normalized) + "\">" + escapeHtml(value) + "</a>";
}

private String formatEmailValue(String email) {
    if (!hasText(email) || "N/A".equalsIgnoreCase(email.trim())) {
        return "N/A";
    }

    String value = email.trim();
    return "<a href=\"mailto:" + escapeHtml(value) + "\">" + escapeHtml(value) + "</a>";
}

private String formatTelegramValue(String username) {
    if (!hasText(username)) {
        return "N/A";
    }

    String value = username.trim();
    if (value.startsWith("@")) {
        value = value.substring(1);
    }

    if (value.isBlank()) {
        return "N/A";
    }

    return "<a href=\"https://t.me/" + escapeHtml(value) + "\">@" + escapeHtml(value) + "</a>";
}

private void restoreReservationExitKeyboard(Long chatId) {
    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);
        telegramClient.sendMultiMedicinePanel(chatId, session.getSelectedMedicines());
        return;
    }

    restoreKeyboard(chatId);
}

  
    private double[] extractCoordinatesFromText(String text) {
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

private String buildPharmacyAddress(Pharmacy pharmacy) {
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


private EthiopiaLocationOption resolveNearestCatalogLocation(double lat, double lon) {
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
private String buildDisplayLocationName(String region, String city, String subCity, String area) {
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
private List<PharmacyResponseDTO> applySearchFilter(List<PharmacyResponseDTO> results, SearchFilterType filterType) {
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
private void applyAndSendMedicineFilter(Long chatId, SearchFilterType filterType, String activeLabel) {
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
private boolean isOpenNow(java.time.LocalTime open, java.time.LocalTime close) {
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


private String localizedReservationStatus(Long chatId, MedicineReservationStatus status) {
    return switch (status) {
        case PENDING -> localizationService.text(chatId, "pending_button");
        case APPROVED -> localizationService.text(chatId, "approved_status");
        case FULFILLED -> localizationService.text(chatId, "fulfilled_button");
        case CANCELLED -> localizationService.text(chatId, "cancelled_button");
        case EXPIRED -> localizationService.text(chatId, "expired_button");
        case REJECTED -> localizationService.text(chatId, "reservation_rejected_user").split("\\n")[0].replace("❌ ", "");
    };
}

private void sendUserReservationSection(Long chatId,
                                        MedicineReservationStatus status,
                                        String titleKey) {
    List<MedicineReservation> reservations = reservationService.getUserReservations(chatId);
    if (reservations == null || reservations.isEmpty()) {
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "no_reservations_found"), "HTML");
        return;
    }

    sendReservationSection(chatId, reservations, status, localizationService.text(chatId, titleKey));
}

private void sendReservationSection(Long chatId,
                                    List<MedicineReservation> reservations,
                                    MedicineReservationStatus status,
                                    String title) {
    List<MedicineReservation> filtered = reservations.stream()
            .filter(r -> r.getStatus() == status)
            .toList();

    if (filtered.isEmpty()) {
    telegramClient.sendMyReservationsSectionMenu(chatId, title);
    telegramClient.sendMessage(
        chatId,
        "📭 No reservations found in this section yet.\n\n" +
            "Open another section. When reservation cards appear, they include 🔁 Reserve Again."
    );
        return;
    }

    telegramClient.sendMyReservationsSectionMenu(chatId, title);

for (MedicineReservation r : filtered) {
        Pharmacy pharmacy = pharmacyRepository.findById(r.getPharmacyId()).orElse(null);
        PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(r.getPharmacyId(), r.getMedicineName())
            .orElse(null);

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
            r.getPharmacyId(),
            inventory == null ? null : inventory.getId(),
                pharmacyName,
                pharmacyAddress,
                r.getMedicineName(),
                r.getRequestedQuantity(),
            localizedReservationStatus(chatId, r.getStatus()),
                holdUntil
        );
    } else {
        telegramClient.sendUserReservationItemReadOnly(
                chatId,
                r.getId(),
            r.getPharmacyId(),
            inventory == null ? null : inventory.getId(),
                pharmacyName,
                pharmacyAddress,
                r.getMedicineName(),
                r.getRequestedQuantity(),
                localizedReservationStatus(chatId, r.getStatus()),
                holdUntil
        );
    }
}
}

private void sendNoResultWithTypoSuggestion(Long chatId, String medicine) {
    boolean medicineExistsInCatalog = pharmacyService.medicineExistsInCatalog(medicine);

    if (medicineExistsInCatalog) {
        telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
        return;
    }

    MedicineSuggestionResult suggestionResult = pharmacyService.suggestMedicineOptions(medicine);
    if (suggestionResult != null && suggestionResult.hasSuggestions()) {
        telegramClient.sendAlternativeMedicineSuggestionsWithNotify(chatId, medicine, suggestionResult);
    } else {
        telegramClient.sendNoMedicineFoundWithNotify(chatId, medicine);
    }
}

private void logSearchIfCatalogMatch(Long chatId, String medicine) {
    if (chatId == null || medicine == null || medicine.isBlank()) {
        return;
    }

    String normalizedMedicine = medicine.trim().toLowerCase();
    if (!pharmacyService.medicineExistsInCatalog(normalizedMedicine)) {
        return;
    }

    medicineSearchLogService.logSearch(chatId, normalizedMedicine);
}

private String buildFullAddress(Pharmacy pharmacy) {
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
private void clearOpenDetailExtras(Long chatId) {
    if (!PharmacyDetailViewSessionManager.exists(chatId)) {
        return;
    }

    PharmacyDetailViewSession session = PharmacyDetailViewSessionManager.get(chatId);

    if (session.getPhotoMessageId() != null) {
        telegramClient.deleteMessage(chatId, session.getPhotoMessageId());
    }

    PharmacyDetailViewSessionManager.remove(chatId);
}

private void sendPharmacyResultsWithTopMap(Long chatId, List<PharmacyResponseDTO> results) {
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
            p.isVerified() ? p.getName() + "  ☑️" : p.getName(),
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
                p.getMedicineId(),
                p.getPrice(),
                p.isOpenNow(),
                p.getOpenTime(),
                p.getCloseTime(),
                p.isTemporarilyClosed(),
                p.getTemporaryClosureReason()
        );
    }
}

private void resendTrackedSearchFilter(Long chatId, String activeFilter) {
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
private boolean handleRegistrationLocationText(Long chatId, String text) {
    if (!RegistrationSessionManager.exists(chatId)) {
        return false;
    }

    RegistrationSession session = RegistrationSessionManager.get(chatId);
    if (session == null || session.getStep() != RegistrationStep.LOCATION) {
        return false;
    }

    String value = text == null ? "" : text.trim();
    String normalized = value.toLowerCase();

    if (session.isWaitingForLicenseExpiryDate()) {
        if (normalized.equals(BACK_ARROW.toLowerCase()) || normalized.equals(BACK_SYMBOL.toLowerCase())) {
            session.setWaitingForLicenseExpiryDate(false);
            session.setWaitingForExactAddress(true);
            telegramClient.sendRegistrationExactAddressPrompt(chatId);
            return true;
        }
        if (normalized.equals(MAIN_HOME.toLowerCase())) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return true;
        }
        if (normalized.equals("❌ cancel")) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            telegramClient.sendMessage(chatId, "❌ Registration cancelled.");
            restoreKeyboard(chatId);
            return true;
        }

        LocalDate expiryDate = parseFutureOrTodayDate(value);
        if (expiryDate == null) {
            telegramClient.sendMessage(chatId, localizationService.text(chatId, "license_expiry_invalid"), "HTML");
            return true;
        }

        session.setLicenseExpiryDate(expiryDate);
        session.setWaitingForLicenseExpiryDate(false);
        telegramClient.sendMessage(chatId, localizationService.text(chatId, "reg_license_step"));
        return true;
    }

    // Handle landmark input / skip
    if (session.isWaitingForLandmark()) {
        if (normalized.equals("⏭ skip landmark")) {
            session.setLandmark(null);
        } else {
            session.setLandmark(value);
        }
        session.setWaitingForLandmark(false);
        session.setWaitingForPlusCode(true);
        telegramClient.sendRegistrationPlusCodePrompt(chatId);
        return true;
    }

    // Handle plus code input / skip
    if (session.isWaitingForPlusCode()) {
        if (normalized.equals(BACK_ARROW.toLowerCase()) || normalized.equals(BACK_SYMBOL.toLowerCase())) {
            session.setWaitingForPlusCode(false);
            session.setWaitingForLandmark(true);
            telegramClient.sendLandmarkChoiceKeyboard(chatId);
            return true;
        }
        if (normalized.equals(MAIN_HOME.toLowerCase())) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return true;
        }
        if (normalized.equals("❌ cancel")) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            telegramClient.sendMessage(chatId, "❌ Registration cancelled.");
            restoreKeyboard(chatId);
            return true;
        }
        if (normalized.equals("⏭ skip plus code")) {
            session.setPlusCode(null);
        } else {
            session.setPlusCode(value);
        }
        session.setWaitingForPlusCode(false);
        session.setWaitingForExactAddress(true);
        telegramClient.sendRegistrationExactAddressPrompt(chatId);
        return true;
    }

    // Handle exact address input / skip
    if (session.isWaitingForExactAddress()) {
        if (normalized.equals(BACK_ARROW.toLowerCase()) || normalized.equals(BACK_SYMBOL.toLowerCase())) {
            session.setWaitingForExactAddress(false);
            session.setWaitingForPlusCode(true);
            telegramClient.sendRegistrationPlusCodePrompt(chatId);
            return true;
        }
        if (normalized.equals(MAIN_HOME.toLowerCase())) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            restoreKeyboard(chatId);
            return true;
        }
        if (normalized.equals("❌ cancel")) {
            session.clearLocationFlags();
            RegistrationSessionManager.remove(chatId);
            telegramClient.sendMessage(chatId, "❌ Registration cancelled.");
            restoreKeyboard(chatId);
            return true;
        }
        if (!normalized.equals("⏭ skip exact address")) {
            session.setFormattedAddress(value);
        }
        session.setWaitingForExactAddress(false);
        session.setLicenseExpiryDateMode();
        telegramClient.sendRegistrationLicenseExpiryPrompt(chatId);
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
        session.setWaitingForPlusCode(true);
        telegramClient.sendRegistrationPlusCodePrompt(chatId);
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
        String canonicalRegion = findCanonicalOption(EthiopiaLocationCatalog.getRegions(), value);

        if (canonicalRegion == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid region from the keyboard.");
            return true;
        }

        session.setSelectedRegion(canonicalRegion);
        session.setCityMode();
        telegramClient.sendCityKeyboard(
                chatId,
                canonicalRegion,
                EthiopiaLocationCatalog.getCitiesByRegion(canonicalRegion)
        );
        return true;
    }

    if (session.isWaitingForCitySelection()) {
        var cities = EthiopiaLocationCatalog.getCitiesByRegion(session.getSelectedRegion());
        String canonicalCity = findCanonicalOption(cities, value);

        if (canonicalCity == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid city from the keyboard.");
            return true;
        }

        session.setSelectedCity(canonicalCity);

        if (EthiopiaLocationCatalog.isAddisAbabaCity(canonicalCity)) {
            session.setSubCityMode();
            telegramClient.sendSubCityKeyboard(
                    chatId,
                    canonicalCity,
                    EthiopiaLocationCatalog.getAddisAbabaSubCities()
            );
            return true;
        }

        session.setAreaMode();
        telegramClient.sendAreaKeyboard(
                chatId,
            canonicalCity,
            EthiopiaLocationCatalog.getAreasByRegionAndCity(session.getSelectedRegion(), canonicalCity)
        );
        return true;
    }

    if (session.isWaitingForSubCitySelection()) {
        var subCities = EthiopiaLocationCatalog.getAddisAbabaSubCities();
        String canonicalSubCity = findCanonicalOption(subCities, value);

        if (canonicalSubCity == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please choose a valid sub-city from the keyboard.");
            return true;
        }

        session.setSelectedSubCity(canonicalSubCity);
        session.setAreaMode();
        telegramClient.sendAreaKeyboard(
                chatId,
                canonicalSubCity,
                EthiopiaLocationCatalog.getAddisAreasBySubCity(canonicalSubCity)
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

    String canonicalArea = findCanonicalOption(areas, value);

    if (canonicalArea == null) {
        telegramClient.sendMessage(chatId, "⚠️ Please choose a valid area from the keyboard.");
        return true;
    }

    session.setArea(canonicalArea);

    EthiopiaLocationOption selected = EthiopiaLocationCatalog.find(
            session.getSelectedRegion(),
            session.getSelectedCity(),
            canonicalArea
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
        session.setWaitingForPlusCode(true);
        telegramClient.sendRegistrationPlusCodePrompt(chatId);
        return true;
    }

    return false;
}
private void finalizeRegistrationLocation(Long chatId, RegistrationSession session) {
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
        summary.append("🗺 Region: ").append(displayLocation(chatId, session.getSelectedRegion())).append("\n");
    }
    if (session.getSelectedCity() != null) {
        summary.append("🏙 City: ").append(displayLocation(chatId, session.getSelectedCity())).append("\n");
    }
    if (session.getSelectedSubCity() != null && !session.getSelectedSubCity().isBlank()) {
        summary.append("🏢 Sub-City: ").append(displayLocation(chatId, session.getSelectedSubCity())).append("\n");
    }
    if (session.getArea() != null) {
        summary.append("📍 Area: ").append(displayLocation(chatId, session.getArea())).append("\n");
    }
    if (session.getFormattedAddress() != null && !session.getFormattedAddress().isBlank()) {
        summary.append("📍 Exact Address: ").append(displayLocationAddress(chatId, session.getFormattedAddress())).append("\n");
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

    // Always start: landmark -> plus code -> exact address -> license expiry -> license upload
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
}
private LocalDate parseFutureOrTodayDate(String input) {
    if (input == null || input.isBlank()) {
        return null;
    }

    try {
        LocalDate parsed = LocalDate.parse(input.trim());
        return parsed.isBefore(LocalDate.now()) ? null : parsed;
    } catch (DateTimeParseException ignored) {
        return null;
    }
}

private double[] parseCoordinates(String input) {
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

private String findCanonicalOption(List<String> options, String input) {
    if (options == null || input == null) {
        return null;
    }

    String trimmed = input.trim();
    if (trimmed.isBlank()) {
        return null;
    }

    return options.stream()
            .filter(option -> option != null && option.equalsIgnoreCase(trimmed))
            .findFirst()
            .orElse(null);
}

private boolean shouldUseEthiopiaCatalog(double lat, double lon, EthiopiaLocationOption nearest) {
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

    private boolean handleAlertRecentCallback(Long chatId, Integer messageId, String data, String callbackId) {
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

    logSearchIfCatalogMatch(chatId, medicine);

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
            "💊 <b>Pharmacies with " + displayMedicine(chatId, medicine) + "</b>\n\nSorted by: <b>Nearest</b>"
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
            MEDICINE_LABEL + displayMedicine(chatId, medicineName) + "\n" +
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

    logSearchIfCatalogMatch(chatId, medicine);

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
    results = applySearchFilter(results, SearchFilterType.NEAREST);

    if (results.isEmpty()) {
        sendNoResultWithTypoSuggestion(chatId, medicine);
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


    private boolean handleFavoritesCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("fav_remove_")) {
    telegramClient.answerCallback(callbackId);

    Long pharmacyId = Long.parseLong(data.substring("fav_remove_".length()));

    try {
        favoritePharmacyService.removeFavorite(chatId, pharmacyId);
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


    private boolean handleAltMedCallback(Long chatId, Integer messageId, String data, String callbackId) {
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

    logSearchIfCatalogMatch(chatId, medicine);

    List<PharmacyResponseDTO> results = pharmacyService.searchMedicineNearby(
            medicine,
            loc.getLatitude(),
            loc.getLongitude(),
            chatId
    );

    MedicineSearchSessionManager.save(chatId, medicine, SearchFilterType.NEAREST);
    results = applySearchFilter(results, SearchFilterType.NEAREST);

    if (results.isEmpty()) {
        sendNoResultWithTypoSuggestion(chatId, medicine);
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


    private boolean handleReservationBasicsCallback(Long chatId, Integer messageId, String data, String callbackId) {
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
        if (data.startsWith("reserve_again_")) {

        Long reservationId;
        try {
            reservationId = Long.parseLong(data.substring("reserve_again_".length()));
        } catch (Exception e) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "⚠️ Invalid reservation id.");
            return true;
        }

        runReserveAgainFlow(chatId, messageId, callbackId, reservationId);
        return true;
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
                MEDICINE_LABEL + displayMedicine(chatId, cancelled.getMedicineName()) + "\n" +
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
                MEDICINE_LABEL + displayMedicine(chatId, reservation.getMedicineName()) + "\n" +
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
                        MEDICINE_LABEL + displayMedicine(pharmacy.getTelegramId(), reservation.getMedicineName()) + "\n" +
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

    restoreReservationExitKeyboard(chatId);
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
            restoreReservationExitKeyboard(chatId);
            return true;
        }
        return false;
    }

    private void redirectReservationToMiniApp(Long chatId, Integer messageId, String callbackId, Long pharmacyId, String medicineName) {
        if (messageId == null || callbackId == null) {
            telegramClient.sendMessage(chatId, "⚠️ Open a pharmacy result and tap Reserve to continue.");
            return;
        }

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId).orElse(null);
        if (pharmacy == null) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "⚠️ Pharmacy not found for cart redirect. Please try again.");
            return;
        }

        PharmacyInventory inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
                .orElse(null);

        if (inventory == null || inventory.getId() == null) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "⚠️ Could not resolve the selected medicine for cart redirect. Please try again.");
            return;
        }

        boolean outOfStock = inventory.isOutOfStock()
                || inventory.getQuantity() == null
                || inventory.getQuantity() <= 0;
        boolean canRate = !ratingService.hasUserRated(pharmacyId, chatId);

        clearMiniAppReservationHandoffState(chatId);

        telegramClient.answerCallback(callbackId, "Open mini app and add this item to cart?", true);
        telegramClient.showMiniAppReserveConfirmation(
                chatId,
                messageId,
                pharmacyId,
                medicineName,
                inventory.getId(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                pharmacy.getPhone(),
                outOfStock,
                canRate
        );
    }

        private void runReserveAgainFlow(Long chatId, Integer messageId, String callbackId, Long reservationId) {
        MedicineReservation previousReservation = reservationService.getUserReservations(chatId).stream()
            .filter(r -> reservationId.equals(r.getId()))
            .findFirst()
            .orElse(null);

        if (previousReservation == null) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "⚠️ Reservation not found.");
            return;
        }

        Pharmacy pharmacy = pharmacyRepository.findById(previousReservation.getPharmacyId())
            .orElse(null);
        if (pharmacy == null) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "⚠️ Pharmacy not found for this reservation.");
            return;
        }

        redirectReservationToMiniApp(chatId, messageId, callbackId, pharmacy.getId(), previousReservation.getMedicineName());
        }

    private void clearMiniAppReservationHandoffState(Long chatId) {
        if (chatId == null) {
            return;
        }

        ReservationSessionManager.remove(chatId);
        MultiReservationSessionManager.remove(chatId);
        MultiMedicineSearchSessionManager.remove(chatId);
    }


    private boolean handleMultiMedicineCallback(Long chatId, Integer messageId, String data, String callbackId) {
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
            "📦 <b>One Reservation at a Time</b>\n\n"
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
if (data.equals("multi_pharmacy_back")) {
            telegramClient.answerCallback(callbackId);
            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);
                telegramClient.sendMultiMedicinePanel(chatId, session.getSelectedMedicines());
            } else {
                telegramClient.sendUserDashboard(chatId);
            }
            return true;
        }
if (data.startsWith("multi_reserve_unavailable_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Multi-medicine session expired.");
                return true;
            }

            Long pharmacyId = Long.parseLong(data.substring("multi_reserve_unavailable_".length()));

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);

            if (session.getSelectedMedicines() == null || session.getSelectedMedicines().isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No selected medicines found.");
                return true;
            }

            Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            List<PharmacyInventory> inventoryList = inventoryRepository.findByPharmacyId(pharmacy.getId());

                List<PharmacyInventory> matchedInventories = session.getSelectedMedicines().stream()
                    .map(selected -> inventoryList.stream().filter(item ->
                        item.getMedicineName() != null
                            && item.getMedicineName().equalsIgnoreCase(selected)
                            && !item.isOutOfStock()
                            && item.getQuantity() != null
                            && item.getQuantity() > 0
                    ).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();

                if (matchedInventories.isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No matched medicines available.");
                return true;
            }

                clearMiniAppReservationHandoffState(chatId);
                telegramClient.sendMiniAppMultiReservePrompt(
                    chatId,
                    pharmacyId,
                    matchedInventories.stream().map(PharmacyInventory::getId).toList()
                );
            return true;
        }
if (data.startsWith("multi_res_qty_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiReservationSession session = MultiReservationSessionManager.get(chatId);

            String payload = data.substring("multi_res_qty_".length());
            int lastUnderscore = payload.lastIndexOf("_");

            if (lastUnderscore == -1) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid data.");
                return true;
            }

            String medicineName = payload.substring(0, lastUnderscore);
            int qty = Integer.parseInt(payload.substring(lastUnderscore + 1));

            session.setQuantityForMedicine(medicineName, qty);
            telegramClient.sendMessage(chatId, "✅ " + telegramClient.displayMedicine(chatId, medicineName) + ": " + qty + " units selected.");

            return true;
        }
if (data.startsWith("multi_res_edit_qty_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            String medicineName = data.substring("multi_res_edit_qty_".length());

            telegramClient.sendMultiReserveQuantityEdit(chatId, medicineName);
            return true;
        }
if (data.equals("multi_res_continue")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiReservationSession session = MultiReservationSessionManager.get(chatId);

            if (!session.allMedicinesHaveQuantities()) {
                telegramClient.sendMessage(chatId, "⚠️ Please set quantities for all medicines.");
                return true;
            }

            telegramClient.sendMultiReserveSummary(chatId, session.getMedicineQuantities());
            session.setCurrentStep("ENTERING_NAME");
            session.setWaitingForName(true);

            return true;
        }
if (data.equals("multi_res_cancel")) {
            telegramClient.answerCallback(callbackId);

            if (MultiReservationSessionManager.exists(chatId)) {
                MultiReservationSessionManager.remove(chatId);
            }

            if (MultiMedicineSearchSessionManager.exists(chatId)) {
                MultiMedicineSearchSession multiSession = MultiMedicineSearchSessionManager.get(chatId);
                telegramClient.sendMultiMedicinePanel(chatId, multiSession.getSelectedMedicines());
            } else {
                telegramClient.sendUserDashboard(chatId);
            }

            telegramClient.sendMessage(chatId, "❌ Multi-medicine reservation cancelled.");
            return true;
        }
if (data.startsWith("multi_res_qty_confirm_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            String payload = data.substring("multi_res_qty_confirm_".length());
            int lastUnderscore = payload.lastIndexOf("_");

            if (lastUnderscore == -1) {
                return true;
            }

            String medicineName = payload.substring(0, lastUnderscore);
            int qty = Integer.parseInt(payload.substring(lastUnderscore + 1));

            MultiReservationSession session = MultiReservationSessionManager.get(chatId);
            session.setQuantityForMedicine(medicineName, qty);

            telegramClient.sendMessage(chatId, "✅ " + telegramClient.displayMedicine(chatId, medicineName) + ": " + qty + " units.");
            telegramClient.sendMultiReserveMedicineQuantityPicker(chatId, session.getMatchedMedicines());

            return true;
        }
if (data.startsWith("multi_res_qty_skip_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            String medicineName = data.substring("multi_res_qty_skip_".length());

            MultiReservationSession session = MultiReservationSessionManager.get(chatId);
            session.setQuantityForMedicine(medicineName, null);

            telegramClient.sendMessage(chatId, "⏭️ Skipped: " + telegramClient.displayMedicine(chatId, medicineName));
            telegramClient.sendMultiReserveMedicineQuantityPicker(chatId, session.getMatchedMedicines());

            return true;
        }
if (data.equals("multi_res_submit")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiReservationSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Session expired.");
                return true;
            }

            MultiReservationSession session = MultiReservationSessionManager.get(chatId);

            if (session.getCustomerName() == null || session.getCustomerPhone() == null) {
                telegramClient.sendMessage(chatId, "⚠️ Please complete name and phone entry.");
                return true;
            }

            try {
                List<MedicineReservation> groupedReservations = reservationService.createReservationGroup(
                        chatId,
                        session.getPharmacyId(),
                        session.getMedicineQuantities(),
                        session.getCustomerPhone(),
                        session.getCustomerName()
                );

                if (groupedReservations.isEmpty()) {
                    telegramClient.sendMessage(chatId, "⚠️ No reservations created.");
                    return true;
                }

                String groupId = groupedReservations.get(0).getReservationGroupId();

                telegramClient.sendMultiReserveGroupedConfirmation(chatId, groupId, groupedReservations);

                Pharmacy pharmacy = pharmacyRepository.findById(session.getPharmacyId())
                        .orElse(null);

                if (pharmacy != null && pharmacy.getTelegramId() != null) {
                    telegramClient.sendPharmacyGroupedReservationCard(pharmacy.getTelegramId(), groupId, groupedReservations);
                }

                MultiReservationSessionManager.remove(chatId);
                if (MultiMedicineSearchSessionManager.exists(chatId)) {
                    MultiMedicineSearchSessionManager.remove(chatId);
                }

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "❌ Error creating reservations: " + e.getMessage());
            }

            return true;
        }
if (data.startsWith("approve_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("approve_group_".length());

            List<MedicineReservation> reservations = reservationRepository.findByReservationGroupIdAndStatus(
                    groupId,
                    MedicineReservationStatus.PENDING
            );

            for (MedicineReservation res : reservations) {
                try {
                    reservationService.approveReservation(res.getId());
                } catch (Exception e) {
                    System.out.println("Error approving reservation " + res.getId() + ": " + e.getMessage());
                }
            }

            telegramClient.sendMessage(chatId, "✅ All reservations in group approved.");
            return true;
        }
if (data.startsWith("review_pres_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("review_pres_group_".length());

            try {
                telegramClient.sendPharmacyPrescriptionReviewCard(
                        chatId,
                        prescriptionReviewService.getPrescriptionStatus(null, groupId, null)
                );
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("pres_files_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("pres_files_group_".length());

            try {
                sendPrescriptionFilesToPharmacy(chatId, prescriptionReviewService.getPrescriptionStatus(null, groupId, null));
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("pres_approve_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("pres_approve_group_".length());

            try {
                PrescriptionStatusResponseDTO response = prescriptionReviewService.reviewPrescription(
                        null,
                        groupId,
                        chatId,
                        PrescriptionReviewRequestDTO.builder().decision("approve").build()
                );
                telegramClient.sendMessage(chatId, "✅ Prescription approved. You can now approve the reservation group.");
                notifyPrescriptionDecision(response, true);

                List<MedicineReservation> reservations = reservationRepository.findByReservationGroupIdOrderByCreatedAtDesc(groupId);
                if (!reservations.isEmpty()) {
                    telegramClient.sendPharmacyPendingGroupedReservationCard(chatId, groupId, reservations);
                }
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("pres_reject_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("pres_reject_group_".length());

            try {
                PrescriptionStatusResponseDTO response = prescriptionReviewService.reviewPrescription(
                        null,
                        groupId,
                        chatId,
                        PrescriptionReviewRequestDTO.builder()
                                .decision("reject")
                                .rejectionReason("Prescription rejected by pharmacy")
                                .build()
                );
                telegramClient.sendMessage(chatId, "❌ Prescription rejected. The reservation group was rejected.");
                notifyPrescriptionDecision(response, false);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("reject_group_")) {
            telegramClient.answerCallback(callbackId);

            String groupId = data.substring("reject_group_".length());

            List<MedicineReservation> reservations = reservationRepository.findByReservationGroupIdAndStatus(
                    groupId,
                    MedicineReservationStatus.PENDING
            );

            for (MedicineReservation res : reservations) {
                try {
                    reservationService.rejectReservation(res.getId(), "Rejected by pharmacy");
                } catch (Exception e) {
                    System.out.println("Error rejecting reservation " + res.getId() + ": " + e.getMessage());
                }
            }

            telegramClient.sendMessage(chatId, "❌ All reservations in group rejected.");
            return true;
        }
if (data.startsWith("multi_reserve_one_")) {
            telegramClient.answerCallback(callbackId);

            if (!MultiMedicineSearchSessionManager.exists(chatId)) {
                telegramClient.sendMessage(chatId, "⚠️ Multi-medicine session expired.");
                return true;
            }

            Long pharmacyId = Long.parseLong(data.substring("multi_reserve_one_".length()));

            MultiMedicineSearchSession session = MultiMedicineSearchSessionManager.get(chatId);

            if (session.getSelectedMedicines() == null || session.getSelectedMedicines().isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No selected medicines found.");
                return true;
            }

            Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            List<PharmacyInventory> inventoryList = inventoryRepository.findByPharmacyId(pharmacy.getId());

                List<PharmacyInventory> matchedInventories = session.getSelectedMedicines().stream()
                    .map(selected -> inventoryList.stream().filter(item ->
                        item.getMedicineName() != null
                            && item.getMedicineName().equalsIgnoreCase(selected)
                            && !item.isOutOfStock()
                            && item.getQuantity() != null
                            && item.getQuantity() > 0
                    ).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();

                if (matchedInventories.isEmpty()) {
                telegramClient.sendMessage(chatId, "⚠️ No matched medicines available for reservation.");
                return true;
            }

                if (matchedInventories.size() == 1) {
            Long medicineId = matchedInventories.get(0).getId();

            clearMiniAppReservationHandoffState(chatId);
            telegramClient.sendMiniAppSingleReservePrompt(chatId, pharmacyId, medicineId);
    return true;
}

                telegramClient.sendMatchedMedicineReservePicker(chatId, pharmacyId, matchedInventories);
            return true;
        }
        return false;
    }


    private boolean handleReservationQuantityCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("res_qty_pick_")) {
    String payload = data.substring("res_qty_pick_".length());
    int firstUnderscore = payload.indexOf("_");
    int lastUnderscore = payload.lastIndexOf("_");

    if (firstUnderscore == -1 || lastUnderscore == -1 || firstUnderscore == lastUnderscore) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid quantity data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1, lastUnderscore);
    redirectReservationToMiniApp(chatId, messageId, callbackId, pharmacyId, medicineName);
    return true;
}
if (data.startsWith("close_reserve_")) {
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

        telegramClient.answerCallback(callbackId);
        telegramClient.restorePharmacyCardButtons(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            inventory == null ? null : inventory.getId(),
            outOfStock,
            pharmacy.getOpenTime() != null
                    && pharmacy.getCloseTime() != null
                    && !isTemporaryClosureActive(pharmacy)
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            !ratingService.hasUserRated(pharmacyId, chatId)
        );
    return true;
}
if (data.startsWith("res_qty_inline_")) {
    String payload = data.substring("res_qty_inline_".length());
    String[] parts = payload.split("_");

    if (parts.length < 3) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve quantity data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(parts[0]);
    String medicineName = parts[1];
    redirectReservationToMiniApp(chatId, messageId, callbackId, pharmacyId, medicineName);
    return true;
}
if (data.startsWith("cancel_mini_app_reserve_")) {
    String payload = data.substring("cancel_mini_app_reserve_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.answerCallback(callbackId);
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve cancel data.");
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

    telegramClient.answerCallback(callbackId);
    telegramClient.restorePharmacyCardButtons(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            inventory == null ? null : inventory.getId(),
            outOfStock,
            pharmacy.getOpenTime() != null
                && pharmacy.getCloseTime() != null
                && !isTemporaryClosureActive(pharmacy)
                && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            !ratingService.hasUserRated(pharmacyId, chatId)
    );
    return true;
}
if (data.startsWith("toggle_reserve_")) {
    String payload = data.substring("toggle_reserve_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.answerCallback(callbackId);
        telegramClient.sendMessage(chatId, "⚠️ Invalid reserve data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

    if (isTemporaryClosureActive(pharmacy)) {
        String blockedMessage = localizationService.text(
                chatId,
                "reservation_blocked_temp_closed",
                pharmacy.getTemporaryClosedUntil() == null
                        ? localizationService.text(chatId, "card_closed")
                        : pharmacy.getTemporaryClosedUntil().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        );
        telegramClient.answerCallback(callbackId);
        telegramClient.sendMessage(chatId, blockedMessage);
        return true;
    }

    redirectReservationToMiniApp(chatId, messageId, callbackId, pharmacyId, medicineName);
    return true;
}
        return false;
    }


    private boolean handleRateAndCallCallback(Long chatId, Integer messageId, String data, String callbackId) {
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
            inventory == null ? null : inventory.getId(),
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
            inventory == null ? null : inventory.getId(),
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
if (data.startsWith("report_issue_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("report_issue_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid issue report data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
    String medicineName = payload.substring(firstUnderscore + 1);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
        PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

        Long medicineId = inventory == null ? null : inventory.getId();
        boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;
        boolean reserveAvailable = !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());
        boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);

        telegramClient.editPharmacyMessageIssueMenu(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            medicineId,
            outOfStock,
            reserveAvailable,
            isFavorite,
            true
        );
    return true;
}
if (data.equals("issue_menu_title")) {
    telegramClient.answerCallback(callbackId);
    return true;
}
    if (data.startsWith("issue_back_")) {
        telegramClient.answerCallback(callbackId);

        String payload = data.substring("issue_back_".length());
        int firstUnderscore = payload.indexOf("_");
        if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid issue back data.");
        return true;
        }

        Long pharmacyId = Long.parseLong(payload.substring(0, firstUnderscore));
        String medicineName = payload.substring(firstUnderscore + 1);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
        PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

        Long medicineId = inventory == null ? null : inventory.getId();
        boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;
        boolean reserveAvailable = !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());
        boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);

        telegramClient.editPharmacyMessageIssueMenu(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            medicineId,
            outOfStock,
            reserveAvailable,
            isFavorite,
            false
        );
        return true;
    }
if (data.startsWith("issue_type_")) {
    String payload = data.substring("issue_type_".length());
    int firstUnderscore = payload.indexOf("_");
    int secondUnderscore = payload.indexOf("_", firstUnderscore + 1);

    if (firstUnderscore == -1 || secondUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid issue type data.");
        return true;
    }

    String issueType = payload.substring(0, firstUnderscore);
    Long pharmacyId = Long.parseLong(payload.substring(firstUnderscore + 1, secondUnderscore));
    String medicineName = payload.substring(secondUnderscore + 1);

    if ("cancel".equalsIgnoreCase(issueType)) {
        telegramClient.answerCallback(callbackId);
        pendingIssueReports.remove(chatId);
        telegramClient.sendMessage(chatId, "❌ Issue report cancelled.");
        return true;
    }

    if ("other".equalsIgnoreCase(issueType)) {
        pendingIssueReports.put(chatId, new PendingIssueReport(pharmacyId, medicineName));
        telegramClient.answerCallback(
            callbackId,
            localizationService.text(chatId, "issue_report_prompt_other"),
            true
        );
        return true;
    }

        telegramClient.answerCallback(callbackId);

        submitIssueReport(chatId, pharmacyId, medicineName, issueType, null);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
        PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

        Long medicineId = inventory == null ? null : inventory.getId();
        boolean outOfStock = inventory == null
            || inventory.isOutOfStock()
            || inventory.getQuantity() == null
            || inventory.getQuantity() <= 0;
        boolean reserveAvailable = !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());
        boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);

        telegramClient.editPharmacyMessageIssueMenu(
            chatId,
            messageId,
            pharmacyId,
            medicineName,
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            pharmacy.getPhone(),
            medicineId,
            outOfStock,
            reserveAvailable,
            isFavorite,
            false
        );
    return true;
}

if (data.startsWith("confirm_reservation_")) {
    telegramClient.answerCallback(callbackId);

    if (!ReservationSessionManager.exists(chatId)) {
        telegramClient.sendMessage(chatId, "⚠️ Reservation session expired. Please start again.");
        return true;
    }

    var session = ReservationSessionManager.get(chatId);
    
    try {
        var reservation = reservationService.createReservation(
                chatId,
                session.getPharmacyId(),
                session.getMedicineName(),
                session.getQuantity(),
                session.getCustomerPhone(),
                session.getCustomerName()
        );

        try {
            reservationWorkflowService.notifyPharmacyPendingReservation(reservation, pendingTimeoutMinutes);

            telegramClient.sendMessage(
                    chatId,
                    "✅ Reservation request sent to pharmacy.\n\n"
                            + MEDICINE_LABEL + session.getMedicineName() + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + session.getCustomerPhone() + "\n"
                            + "🕒 Waiting for pharmacy approval.\n"
                            + "⏱ Auto-cancels in " + pendingTimeoutMinutes + " minutes if not approved."
            );

        } catch (Exception notifyError) {
            telegramClient.sendMessage(
                    chatId,
                    "✅ Reservation saved.\n\n"
                            + MEDICINE_LABEL + session.getMedicineName() + "\n"
                            + QUANTITY_LABEL + session.getQuantity() + "\n"
                            + "👤 Name: " + session.getCustomerName() + "\n"
                            + PHONE_LABEL + session.getCustomerPhone() + "\n\n"
                            + "⚠️ Could not notify the pharmacy automatically."
            );
        }

    } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
    } finally {
        ReservationSessionManager.remove(chatId);
        restoreKeyboard(chatId);
    }

    return true;
}

if (data.startsWith("edit_res_name_")) {
    telegramClient.answerCallback(callbackId);

    if (!ReservationSessionManager.exists(chatId)) {
        telegramClient.sendMessage(chatId, "⚠️ Reservation session expired. Please start again.");
        return true;
    }

    var session = ReservationSessionManager.get(chatId);
    session.setEditingName(true);
    session.setEditingPhone(false);
    
    telegramClient.sendMessage(
            chatId,
            "👤 Please enter your full name again.\n\nExample:\nTeketsel Beyene"
    );
    return true;
}

if (data.startsWith("edit_res_phone_")) {
    telegramClient.answerCallback(callbackId);

    if (!ReservationSessionManager.exists(chatId)) {
        telegramClient.sendMessage(chatId, "⚠️ Reservation session expired. Please start again.");
        return true;
    }

    var session = ReservationSessionManager.get(chatId);
    session.setEditingPhone(true);
    session.setEditingName(false);
    
    telegramClient.sendPhoneRequestKeyboard(
            chatId,
            "📱 Please share your phone number again.\n\nYou can tap the button below or type it manually."
    );
    return true;
}

if (data.startsWith("cancel_reservation_")) {
    telegramClient.answerCallback(callbackId);

    if (ReservationSessionManager.exists(chatId)) {
        ReservationSessionManager.remove(chatId);
    }

    telegramClient.sendMessage(chatId, "❌ Reservation cancelled.");
    restoreReservationExitKeyboard(chatId);
    return true;
}

if (data.startsWith("call_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("call_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
    Long pharmacyId = Long.parseLong(payload);
    Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
        .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));
    String phone = pharmacy.getPhone() == null ? "N/A" : pharmacy.getPhone().trim();
    telegramClient.answerCallback(callbackId, "📞 " + pharmacy.getName() + ": " + phone, false);
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
    boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);
    boolean outOfStock = item == null || item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0;
    Integer stockQuantity = item == null ? 0 : item.getQuantity();
    BigDecimal price = item == null ? null : item.getPrice();

    telegramClient.editPharmacyMessageToCompactWithCall(
        chatId,
        messageId,
        formatVerifiedPharmacyName(pharmacy),
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
        item == null ? null : item.getId(),
        price,
        pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
            && !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
        pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
        pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
        isTemporaryClosureActive(pharmacy),
        isTemporaryClosureActive(pharmacy) ? pharmacy.getTemporaryClosureReason() : null,
        isFavorite,
        false
    );

    return true;
}
if (data.startsWith("copy_call_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("copy_call_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid copy call request.");
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
    boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);
    boolean outOfStock = item == null || item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0;
    Integer stockQuantity = item == null ? 0 : item.getQuantity();
    BigDecimal price = item == null ? null : item.getPrice();

    telegramClient.editPharmacyMessageToCompactWithCall(
        chatId,
        messageId,
        formatVerifiedPharmacyName(pharmacy),
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
        item == null ? null : item.getId(),
        price,
        pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
            && !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
        pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
        pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
        isTemporaryClosureActive(pharmacy),
        isTemporaryClosureActive(pharmacy) ? pharmacy.getTemporaryClosureReason() : null,
        isFavorite,
        true
    );
    return true;
}
if (data.startsWith("hide_call_")) {
    telegramClient.answerCallback(callbackId);

    String payload = data.substring("hide_call_".length());
    int firstUnderscore = payload.indexOf("_");

    if (firstUnderscore == -1) {
    telegramClient.sendMessage(chatId, "⚠️ Invalid hide call request.");
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
    boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);
    boolean outOfStock = item == null || item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0;
    Integer stockQuantity = item == null ? 0 : item.getQuantity();
    BigDecimal price = item == null ? null : item.getPrice();

    telegramClient.editPharmacyMessageToCompact(
        chatId,
        messageId,
        formatVerifiedPharmacyName(pharmacy),
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
        item == null ? null : item.getId(),
        price,
        pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
            && !isTemporaryClosureActive(pharmacy)
            && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
        pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
        pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
        isTemporaryClosureActive(pharmacy),
        isTemporaryClosureActive(pharmacy) ? pharmacy.getTemporaryClosureReason() : null,
        isFavorite
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

    private void submitIssueReport(Long chatId,
                                   Long pharmacyId,
                                   String medicineName,
                                   String issueType,
                                   String details) {
        String dedupKey = chatId + "_" + pharmacyId + "_" + medicineName + "_" + (issueType == null ? "other" : issueType.toLowerCase());
        long now = System.currentTimeMillis();
        Long lastReported = submittedIssueReports.get(dedupKey);
        if (lastReported != null && (now - lastReported) < ISSUE_REPORT_COOLDOWN_MS) {
            telegramClient.sendMessage(chatId, localizationService.text(chatId, "issue_already_reported"));
            return;
        }
        submittedIssueReports.put(dedupKey, now);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

        String issueTypeLabel = switch (issueType == null ? "other" : issueType.toLowerCase()) {
            case "price" -> localizationService.text(chatId, "issue_type_price");
            case "stock" -> localizationService.text(chatId, "issue_type_stock");
            case "location" -> localizationService.text(chatId, "issue_type_location");
            case "service" -> localizationService.text(chatId, "issue_type_service");
            default -> localizationService.text(chatId, "issue_type_other");
        };

        String issueMessage = (details != null && !details.isBlank())
            ? issueTypeLabel + "\n" + details
            : issueTypeLabel;
        adminInboxService.createIssueItem(chatId, pharmacyId, medicineName, issueType, issueMessage);

        telegramClient.sendMessage(chatId, localizationService.text(chatId, "issue_report_received"));

        StringBuilder adminMsg = new StringBuilder();
        adminMsg.append("⚠️ <b>User Issue Report</b>\n\n")
                .append("👤 User ID: ").append(chatId).append("\n")
                .append("🏥 Pharmacy: ").append(pharmacy.getName()).append("\n")
                .append("💊 Medicine: ").append(medicineName).append("\n")
                .append("🧩 Issue Type: ").append(issueTypeLabel).append("\n")
                .append("🆔 Pharmacy ID: ").append(pharmacyId);

        if (details != null && !details.isBlank()) {
            adminMsg.append("\n📝 Details: ").append(details);
        }

        telegramClient.sendMessage(ADMIN_CHAT_ID, adminMsg.toString());
    }

    private void notifyAdminFeedback(Long chatId, String feedbackText) {
        String name = resolveFeedbackName(chatId);
        String phone = resolveFeedbackPhone(chatId);
        String safeFeedback = hasText(feedbackText) ? escapeHtml(feedbackText.trim()) : "N/A";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        adminInboxService.createFeedbackItem(chatId, feedbackText);

        String adminMsg = "📝 <b>New Feedback</b>\n\n"
                + "👤 <b>Name:</b> " + escapeHtml(name) + "\n"
                + "📞 <b>Phone:</b> " + escapeHtml(phone) + "\n"
                + "🆔 <b>User ID:</b> " + chatId + "\n"
                + "🕒 <b>Time:</b> " + timestamp + "\n"
                + "💬 <b>Feedback:</b> " + safeFeedback;

        telegramClient.sendMessage(ADMIN_CHAT_ID, adminMsg);
    }

    private String resolveFeedbackName(Long chatId) {
        List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(chatId);
        for (MedicineReservation reservation : reservations) {
            if (hasText(reservation.getCustomerName())) {
                return reservation.getCustomerName().trim();
            }
        }

        return pharmacyRepository.findByTelegramId(chatId)
                .map(Pharmacy::getName)
                .filter(this::hasText)
                .map(String::trim)
                .orElse("N/A");
    }

    private String resolveFeedbackPhone(Long chatId) {
        List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(chatId);
        for (MedicineReservation reservation : reservations) {
            if (hasText(reservation.getCustomerPhone())) {
                return reservation.getCustomerPhone().trim();
            }
        }

        return pharmacyRepository.findByTelegramId(chatId)
                .map(Pharmacy::getPhone)
                .filter(this::hasText)
                .map(String::trim)
                .orElse("N/A");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    private boolean handleMedSelectionCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("multi_pick_reserve_cancel")) {
            telegramClient.answerCallback(callbackId);
            telegramClient.sendMessage(chatId, "❌ Reservation selection cancelled.");
            restoreReservationExitKeyboard(chatId);
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

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
            .orElse(null);

    if (inventory == null || inventory.getId() == null) {
        telegramClient.sendMessage(chatId, "⚠️ Could not resolve the selected medicine for mini app redirect.");
        return true;
    }

    if (MultiMedicineSearchSessionManager.exists(chatId)) {
        MultiMedicineSearchSessionManager.get(chatId).setWaitingForMedicineInput(false);
    }

    clearMiniAppReservationHandoffState(chatId);
    telegramClient.sendMiniAppSingleReservePrompt(chatId, pharmacyId, inventory.getId());
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
                String medicines = MedicineSearchNormalizer.normalizeCommaSeparatedMedicines(
                        String.join(",", medSession.getSelectedMedicines())
                );

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


    private void showPharmacyOwnPhotos(Long chatId) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
            List<String> files = pharmacyPhotoService.getOrderedFileIds(pharmacy.getId());

            if (files.isEmpty()) {
                telegramClient.sendMessage(chatId, "No pharmacy photos available yet.");
                return;
            }

            telegramClient.sendPharmacyPhotosForUser(
                    chatId,
                    pharmacy.getName(),
                    displayLocation(chatId, pharmacy.getArea()),
                    pharmacy.getLandmark(),
                    files
            );
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startMedicinePhotoSelectionFlow(Long chatId, String action) {
        try {
            List<PharmacyInventory> inventory = inventoryService.getInventory(chatId);
            if (inventory == null || inventory.isEmpty()) {
                telegramClient.sendMessage(chatId, "No medicines found in your inventory.");
                return;
            }

            String title = switch (action) {
                case "add" -> "Select Medicine for Photo Upload";
                case "view" -> "Select Medicine to View Photos";
                case "set_main" -> "Select Medicine to Set Main Photo";
                case "remove" -> "Select Medicine to Remove Photo";
                default -> "Select Medicine";
            };

            telegramClient.sendPharmacyMedicineIndexedList(chatId, title, inventory);
            telegramClient.sendMessage(chatId, "Send the number of the medicine.");
            medicinePhotoPendingAction.put(chatId, action);
            UpdateSessionManager.start(chatId, UpdateField.MEDICINE_PHOTO_SELECT);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startInventoryPrescriptionSelectionFlow(Long chatId) {
        try {
            List<PharmacyInventory> inventory = inventoryService.getInventory(chatId);
            if (inventory == null || inventory.isEmpty()) {
                telegramClient.sendMessage(chatId, "No medicines found in your inventory.");
                return;
            }

            telegramClient.sendPharmacyMedicineIndexedList(chatId, "Select Medicine for Prescription Setting", inventory);
            telegramClient.sendMessage(chatId, "Send the number of the medicine.");
            UpdateSessionManager.start(chatId, UpdateField.INVENTORY_PRESCRIPTION_SELECT);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startAdminInventoryPrescriptionSelectionFlow(Long chatId, Long pharmacyId) {
        try {
            List<PharmacyInventory> inventory = inventoryService.getInventoryByPharmacyId(pharmacyId);
            if (inventory == null || inventory.isEmpty()) {
                telegramClient.sendMessage(chatId, "No medicines found for this pharmacy.");
                return;
            }

            AdminPharmacyManagementSessionManager.save(
                    chatId,
                    new AdminPharmacyManagementSession("PRESCRIPTION_PICK:" + pharmacyId, String.valueOf(pharmacyId))
            );
            telegramClient.sendPharmacyMedicineIndexedList(chatId, "Select Medicine for Prescription Setting", inventory);
            telegramClient.sendMessage(chatId, "Send the number of the medicine.");
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void handleAdminPrescriptionMedicineSelection(Long chatId,
                                                          AdminPharmacyManagementSession session,
                                                          String originalText) {
        try {
            Long pharmacyId = Long.parseLong(session.getMode().substring("PRESCRIPTION_PICK:".length()));
            Integer idx;
            try {
                idx = Integer.parseInt(originalText == null ? "" : originalText.trim());
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "⚠️ Send a valid number from the list.");
                return;
            }

            PharmacyInventory selected = requireMedicineByIndexForPharmacy(pharmacyId, idx);
            telegramClient.sendPharmacyPrescriptionSettingCard(
                    chatId,
                    selected.getId(),
                    selected.getMedicineName(),
                    selected.getQuantity(),
                    selected.getPrice(),
                    selected.getCurrency(),
                    selected.isRequiresPrescription(),
                    true,
                    pharmacyId
            );
            AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("LIST", null));
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void showMedicineOwnPhotos(Long chatId, Long medicineId) {
        try {
            PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
            List<String> files = medicinePhotoService.getOrderedFileIds(medicine.getId());

            if (files.isEmpty()) {
                telegramClient.sendMessage(chatId,
                        "No medicine photos available yet for " + displayMedicine(chatId, medicine.getMedicineName()) + ".");
                return;
            }

            telegramClient.sendMedicinePhotosForPharmacy(chatId, medicine.getMedicineName(), files);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startSetMainMedicinePhotoFlow(Long chatId, Long medicineId) {
        try {
            PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
            List<com.tenahub.bot.entity.MedicinePhoto> photos = medicinePhotoService.listByMedicineId(medicine.getId());

            if (photos.isEmpty()) {
                telegramClient.sendMessage(chatId,
                        "No medicine photos found for " + displayMedicine(chatId, medicine.getMedicineName()) + ".");
                medicinePhotoPendingMedicineId.remove(chatId);
                return;
            }

            telegramClient.sendMedicinePhotoIndexedList(chatId,
                    "Set Main Photo - " + displayMedicine(chatId, medicine.getMedicineName()), photos);
            telegramClient.sendMessage(chatId, "Send the number to set as main medicine photo.");
            UpdateSessionManager.start(chatId, UpdateField.MEDICINE_PHOTO_SET_MAIN);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startRemoveMedicinePhotoFlow(Long chatId, Long medicineId) {
        try {
            PharmacyInventory medicine = requireOwnedMedicine(chatId, medicineId);
            List<com.tenahub.bot.entity.MedicinePhoto> photos = medicinePhotoService.listByMedicineId(medicine.getId());

            if (photos.isEmpty()) {
                telegramClient.sendMessage(chatId,
                        "No medicine photos found for " + displayMedicine(chatId, medicine.getMedicineName()) + ".");
                medicinePhotoPendingMedicineId.remove(chatId);
                return;
            }

            telegramClient.sendMedicinePhotoIndexedList(chatId,
                    "Remove Photo - " + displayMedicine(chatId, medicine.getMedicineName()), photos);
            telegramClient.sendMessage(chatId, "Send the number to remove.");
            UpdateSessionManager.start(chatId, UpdateField.MEDICINE_PHOTO_REMOVE);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private PharmacyInventory requireOwnedMedicineByIndex(Long chatId, Integer idx) {
        List<PharmacyInventory> inventory = inventoryService.getInventory(chatId);
        if (inventory == null || inventory.isEmpty()) {
            throw new RuntimeException("No medicines found in your inventory");
        }
        if (idx == null || idx < 1 || idx > inventory.size()) {
            throw new RuntimeException("Number out of range");
        }
        return requireOwnedMedicine(chatId, inventory.get(idx - 1).getId());
    }

    private PharmacyInventory requireOwnedMedicine(Long chatId, Long medicineId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

        PharmacyInventory medicine = inventoryRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));

        if (medicine.getPharmacyId() == null || !medicine.getPharmacyId().equals(pharmacy.getId())) {
            throw new RuntimeException("Selected medicine does not belong to your pharmacy");
        }

        return medicine;
    }

    private PharmacyInventory requireMedicineByIndexForPharmacy(Long pharmacyId, Integer idx) {
        List<PharmacyInventory> inventory = inventoryService.getInventoryByPharmacyId(pharmacyId);
        if (inventory == null || inventory.isEmpty()) {
            throw new RuntimeException("No medicines found for this pharmacy");
        }
        if (idx == null || idx < 1 || idx > inventory.size()) {
            throw new RuntimeException("Number out of range");
        }
        return requireMedicineForPharmacy(pharmacyId, inventory.get(idx - 1).getId());
    }

    private PharmacyInventory requireMedicineForPharmacy(Long pharmacyId, Long medicineId) {
        PharmacyInventory medicine = inventoryRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));

        if (medicine.getPharmacyId() == null || !medicine.getPharmacyId().equals(pharmacyId)) {
            throw new RuntimeException("Selected medicine does not belong to this pharmacy");
        }

        return medicine;
    }

    private void startSetMainPhotoFlow(Long chatId) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
            List<com.tenahub.bot.entity.PharmacyPhoto> photos = pharmacyPhotoService.listByPharmacyId(pharmacy.getId());

            if (photos.isEmpty()) {
                telegramClient.sendMessage(chatId, "No photos found.");
                return;
            }

            telegramClient.sendPharmacyPhotoIndexedList(chatId, "Set Main Photo", photos);
            telegramClient.sendMessage(chatId, "Send the number to set as main photo.");
            UpdateSessionManager.start(chatId, UpdateField.PHOTO_SET_MAIN);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void startRemovePhotoFlow(Long chatId) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(chatId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
            List<com.tenahub.bot.entity.PharmacyPhoto> photos = pharmacyPhotoService.listByPharmacyId(pharmacy.getId());

            if (photos.isEmpty()) {
                telegramClient.sendMessage(chatId, "No photos found.");
                return;
            }

            telegramClient.sendPharmacyPhotoIndexedList(chatId, "Remove Photo", photos);
            telegramClient.sendMessage(chatId, "Send the number to remove.");
            UpdateSessionManager.start(chatId, UpdateField.PHOTO_REMOVE);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void sendPharmacyPhotosForUser(Long chatId, Long pharmacyId) {
        try {
            Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                    .orElseThrow(() -> new RuntimeException(PHARMACY_NOT_FOUND));

            pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
            List<String> files = pharmacyPhotoService.getOrderedFileIds(pharmacy.getId());

            telegramClient.sendPharmacyPhotosForUser(
                    chatId,
                    pharmacy.getName(),
                    displayLocation(chatId, pharmacy.getArea()),
                    pharmacy.getLandmark(),
                    files
            );
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void sendAdminInboxSummary(Long chatId) {
        AdminInboxService.InboxCounts counts = adminInboxService.getCounts();
        telegramClient.sendAdminInboxSummary(chatId, counts.newCount(), counts.inReviewCount(), counts.resolvedCount());
    }

    private void sendAdminInboxList(Long chatId, String filter) {
        String normalized = filter == null ? "open_all" : filter.trim().toLowerCase();

        List<AdminInboxItem> items;
        String title;

        switch (normalized) {
            case "new_issue" -> {
                items = adminInboxService.listByStatusAndType(AdminInboxItemStatus.NEW, AdminInboxItemType.ISSUE);
                title = "New Issues";
            }
            case "new_feedback" -> {
                items = adminInboxService.listByStatusAndType(AdminInboxItemStatus.NEW, AdminInboxItemType.FEEDBACK);
                title = "New Feedback";
            }
            case "resolved_all" -> {
                items = adminInboxService.listByStatusAndType(AdminInboxItemStatus.RESOLVED, null);
                title = "Resolved";
            }
            case "open_all" -> {
                items = adminInboxService.listOpen(null);
                title = "All Open";
            }
            default -> {
                items = adminInboxService.listOpen(null);
                title = "All Open";
            }
        }

        telegramClient.sendAdminInboxList(chatId, title, items);
    }

    private void sendAdminLicenseComplianceSummary(Long chatId) {
        adminLicenseCompliancePendingAction.remove(chatId);
        adminLicenseComplianceLastCategory.put(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
        telegramClient.sendAdminLicenseComplianceSummary(chatId, licenseComplianceService.buildSummary());
    }

    private void sendAdminLicenseComplianceCategory(Long chatId, String category) {
        String normalized = normalizeComplianceCategory(category);
        adminLicenseComplianceLastCategory.put(chatId, normalized);

        String title = switch (normalized) {
            case LicenseComplianceService.CATEGORY_EXPIRED -> "Expired";
            case LicenseComplianceService.CATEGORY_MISSING_LICENSE -> "Missing License";
            case LicenseComplianceService.CATEGORY_PENDING_REVIEW -> "Pending Review";
            case LicenseComplianceService.CATEGORY_SUSPENDED -> "Suspended";
            default -> "Expiring Soon";
        };

        telegramClient.sendAdminLicenseComplianceCategory(
                chatId,
                title,
                normalized,
                licenseComplianceService.listByCategory(normalized)
        );
    }

    private void openAdminLicenseComplianceDetail(Long chatId, String category, Long pharmacyId) {
        String normalized = normalizeComplianceCategory(category);
        adminLicenseComplianceLastCategory.put(chatId, normalized);
        adminLicenseComplianceCurrentPharmacy.put(chatId, pharmacyId);
        telegramClient.sendAdminLicenseComplianceDetail(
                chatId,
                normalized,
                licenseComplianceService.getDetail(pharmacyId)
        );
    }

    private String normalizeComplianceCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase();
        return switch (value) {
            case LicenseComplianceService.CATEGORY_EXPIRED -> LicenseComplianceService.CATEGORY_EXPIRED;
            case LicenseComplianceService.CATEGORY_MISSING_LICENSE -> LicenseComplianceService.CATEGORY_MISSING_LICENSE;
            case LicenseComplianceService.CATEGORY_PENDING_REVIEW -> LicenseComplianceService.CATEGORY_PENDING_REVIEW;
            case LicenseComplianceService.CATEGORY_SUSPENDED -> LicenseComplianceService.CATEGORY_SUSPENDED;
            default -> LicenseComplianceService.CATEGORY_EXPIRING_SOON;
        };
    }

    private void sendAdminAuditTrailMenu(Long chatId) {
        adminAuditTrailLastFilter.put(chatId, "recent");
        telegramClient.sendAdminAuditTrailMenu(chatId);
    }

    private void sendAdminAuditTrailList(Long chatId, String filter) {
        String normalized = filter == null ? "recent" : filter.trim().toLowerCase();
        adminAuditTrailLastFilter.put(chatId, normalized);

        List<AdminAuditTrail> records;
        String title;

        switch (normalized) {
            case "pharmacy" -> {
                records = adminAuditTrailService.listRecentByTargetType("PHARMACY");
                title = "Recent Pharmacy Actions";
            }
            case "reservation" -> {
                records = adminAuditTrailService.listRecentByTargetType("RESERVATION");
                title = "Recent Reservation Actions";
            }
            case "compliance" -> {
                records = adminAuditTrailService.listRecentByTargetType("COMPLIANCE");
                title = "Recent Compliance Actions";
            }
            case "inbox" -> {
                records = adminAuditTrailService.listRecentByTargetType("ADMIN_INBOX");
                title = "Recent Inbox Actions";
            }
            default -> {
                records = adminAuditTrailService.listRecent();
                title = "Recent Admin Actions";
            }
        }

        telegramClient.sendAdminAuditTrailList(chatId, title, records);
    }

    private boolean handleAdminAuditTrailTextAction(Long chatId, String normalizedText) {
        if (normalizedText.equals("⬅️ audit trail")) {
            sendAdminAuditTrailMenu(chatId);
            return true;
        }

        if (normalizedText.equals("🕒 recent actions")) {
            sendAdminAuditTrailList(chatId, "recent");
            return true;
        }

        if (normalizedText.equals("🏥 pharmacy actions")) {
            sendAdminAuditTrailList(chatId, "pharmacy");
            return true;
        }

        if (normalizedText.equals("📦 reservation actions")) {
            sendAdminAuditTrailList(chatId, "reservation");
            return true;
        }

        if (normalizedText.equals("📄 compliance actions")) {
            sendAdminAuditTrailList(chatId, "compliance");
            return true;
        }

        if (normalizedText.equals("📥 inbox actions")) {
            sendAdminAuditTrailList(chatId, "inbox");
            return true;
        }

        return false;
    }

    private void recordAudit(Long adminId,
                             String actionType,
                             String targetType,
                             Long targetId,
                             String details) {
        try {
            adminAuditTrailService.record(actionType, targetType, targetId, adminId, details);
        } catch (Exception ignored) {
            // Audit logging should not interrupt operational actions.
        }
    }

    private void recordAudit(Long adminId,
                             String actionType,
                             String targetType,
                             Long targetId,
                             String details,
                             String oldValue,
                             String newValue) {
        try {
            adminAuditTrailService.record(actionType, targetType, targetId, adminId, details, oldValue, newValue);
        } catch (Exception ignored) {
            // Audit logging should not interrupt operational actions.
        }
    }

    private boolean handleAdminLicenseComplianceTextAction(Long chatId, String normalizedText, String originalText) {
        if (normalizedText.equals("🔄 refresh compliance") || normalizedText.equals("⬅️ compliance summary")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceSummary(chatId);
            return true;
        }

        if (normalizedText.equals("⏳ expiring soon")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceCategory(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
            return true;
        }

        if (normalizedText.equals("❌ expired")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceCategory(chatId, LicenseComplianceService.CATEGORY_EXPIRED);
            return true;
        }

        if (normalizedText.equals("📭 missing license")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceCategory(chatId, LicenseComplianceService.CATEGORY_MISSING_LICENSE);
            return true;
        }

        if (normalizedText.equals("🕒 pending review")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceCategory(chatId, LicenseComplianceService.CATEGORY_PENDING_REVIEW);
            return true;
        }

        if (normalizedText.equals("⛔ suspended")) {
            adminLicenseCompliancePendingAction.remove(chatId);
            sendAdminLicenseComplianceCategory(chatId, LicenseComplianceService.CATEGORY_SUSPENDED);
            return true;
        }

        if (normalizedText.equals("📖 open pharmacy by id")) {
            adminLicenseCompliancePendingAction.put(chatId, "open");
            telegramClient.sendMessage(chatId, "🔢 Send pharmacy ID to open compliance detail (example: 12 or #12).");
            return true;
        }

        if (normalizedText.equals("📣 notify pharmacy")) {
            return runComplianceActionOrRequestId(chatId, "notify");
        }

        if (normalizedText.equals("🗓 extend grace +7d")) {
            return runComplianceActionOrRequestId(chatId, "grace");
        }

        if (normalizedText.equals("⛔ suspend pharmacy") || normalizedText.equals("✅ unsuspend pharmacy")) {
            return runComplianceActionOrRequestId(chatId, "toggle_suspend");
        }

        if (normalizedText.equals("✅ clear issue")) {
            return runComplianceActionOrRequestId(chatId, "clear");
        }

        if (normalizedText.equals("📄 view active license")) {
            Long pharmacyId = adminLicenseComplianceCurrentPharmacy.get(chatId);
            if (pharmacyId == null) {
                adminLicenseCompliancePendingAction.put(chatId, "view_active");
                telegramClient.sendMessage(chatId, "🔢 Send pharmacy ID to view active license document.");
                return true;
            }

            sendActiveLicenseDocument(chatId, pharmacyId);
            return true;
        }

        if (normalizedText.equals("📎 view pending license")) {
            Long pharmacyId = adminLicenseComplianceCurrentPharmacy.get(chatId);
            if (pharmacyId == null) {
                adminLicenseCompliancePendingAction.put(chatId, "view_pending");
                telegramClient.sendMessage(chatId, "🔢 Send pharmacy ID to view pending license document.");
                return true;
            }

            sendPendingLicenseDocument(chatId, pharmacyId);
            return true;
        }

        if (normalizedText.equals("⬅️ back to category")) {
            String category = adminLicenseComplianceLastCategory
                    .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
            sendAdminLicenseComplianceCategory(chatId, category);
            return true;
        }

        String pendingAction = adminLicenseCompliancePendingAction.get(chatId);
        if (pendingAction == null || originalText == null) {
            return false;
        }

        Long pharmacyId = parseEntityId(originalText);
        if (pharmacyId == null) {
            telegramClient.sendMessage(chatId, "⚠️ Please send a valid numeric pharmacy ID.");
            return true;
        }

        adminLicenseCompliancePendingAction.remove(chatId);

        if (pendingAction.equals("open")) {
            try {
                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                openAdminLicenseComplianceDetail(chatId, category, pharmacyId);
                adminLicenseComplianceCurrentPharmacy.put(chatId, pharmacyId);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }

        if (pendingAction.equals("view_active")) {
            sendActiveLicenseDocument(chatId, pharmacyId);
            return true;
        }

        if (pendingAction.equals("view_pending")) {
            sendPendingLicenseDocument(chatId, pharmacyId);
            return true;
        }

        if (pendingAction.equals("notify")
                || pendingAction.equals("grace")
                || pendingAction.equals("toggle_suspend")
                || pendingAction.equals("clear")) {
            return runComplianceAction(chatId, pendingAction, pharmacyId);
        }

        return false;
    }

    private boolean runComplianceActionOrRequestId(Long chatId, String action) {
        Long pharmacyId = adminLicenseComplianceCurrentPharmacy.get(chatId);
        if (pharmacyId == null) {
            adminLicenseCompliancePendingAction.put(chatId, action);
            telegramClient.sendMessage(chatId, "🔢 Send pharmacy ID for this compliance action.");
            return true;
        }

        return runComplianceAction(chatId, action, pharmacyId);
    }

    private boolean runComplianceAction(Long chatId, String action, Long pharmacyId) {
        try {
            LicenseComplianceService.ComplianceDetail detail;

            switch (action) {
                case "notify" -> {
                    detail = licenseComplianceService.notifyPharmacy(pharmacyId, chatId);
                    recordAudit(chatId, "COMPLIANCE_NOTIFY_SENT", "COMPLIANCE", pharmacyId,
                            "Compliance notice sent to pharmacy");
                    if (detail.telegramId() != null) {
                        telegramClient.sendMessage(
                                detail.telegramId(),
                                "📄 <b>License Compliance Notice</b>\n\n"
                                        + "Your pharmacy requires license compliance attention.\n"
                                        + "Please update or verify your license details to avoid interruption.",
                                "HTML"
                        );
                    }
                }
                case "grace" -> {
                    detail = licenseComplianceService.extendGracePeriod(pharmacyId, 7, chatId);
                    recordAudit(chatId, "COMPLIANCE_GRACE_EXTENDED", "COMPLIANCE", pharmacyId,
                            "Grace period extended by 7 days");
                    if (detail.telegramId() != null) {
                        telegramClient.sendMessage(
                                detail.telegramId(),
                                "🗓 Admin extended your license compliance grace period by 7 days."
                        );
                    }
                }
                case "toggle_suspend" -> {
                    LicenseComplianceService.ComplianceDetail current = licenseComplianceService.getDetail(pharmacyId);
                    if ("Suspended".equalsIgnoreCase(current.status())) {
                        detail = licenseComplianceService.unsuspendForCompliance(pharmacyId, chatId);
                        recordAudit(chatId, "PHARMACY_UNSUSPENDED", "COMPLIANCE", pharmacyId,
                                "Pharmacy unsuspended from compliance action center");
                        if (detail.telegramId() != null) {
                            telegramClient.sendMessage(detail.telegramId(), "✅ Your pharmacy suspension has been lifted by admin.");
                        }
                    } else {
                        detail = licenseComplianceService.suspendForCompliance(pharmacyId, chatId);
                        recordAudit(chatId, "PHARMACY_SUSPENDED", "COMPLIANCE", pharmacyId,
                                "Pharmacy suspended due to compliance action center");
                        if (detail.telegramId() != null) {
                            telegramClient.sendMessage(
                                    detail.telegramId(),
                                    "⛔ Your pharmacy has been suspended due to license compliance.\n"
                                            + "Please submit valid license information for review."
                            );
                        }
                    }
                }
                case "clear" -> {
                    detail = licenseComplianceService.clearComplianceIssue(pharmacyId, chatId);
                    recordAudit(chatId, "COMPLIANCE_ISSUE_CLEARED", "COMPLIANCE", pharmacyId,
                            "Compliance issue cleared by admin");
                    if (detail.telegramId() != null) {
                        telegramClient.sendMessage(detail.telegramId(), "✅ Your license compliance issue has been cleared by admin.");
                    }
                }
                default -> {
                    return false;
                }
            }

            adminLicenseComplianceCurrentPharmacy.put(chatId, pharmacyId);
            String category = adminLicenseComplianceLastCategory
                    .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
            telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            return true;
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            return true;
        }
    }

    private void sendActiveLicenseDocument(Long chatId, Long pharmacyId) {
        try {
            Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
            if (pharmacy.getLicenseFileId() == null || pharmacy.getLicenseFileId().isBlank()) {
                telegramClient.sendMessage(chatId, "⚠️ Active license file not found.");
                return;
            }

            String caption = "📄 <b>Current License</b>\n\n"
                    + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                    + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                    + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

            telegramClient.sendDocument(chatId, pharmacy.getLicenseFileId(), caption);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private void sendPendingLicenseDocument(Long chatId, Long pharmacyId) {
        try {
            Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
            if (pharmacy.getPendingLicenseFileId() == null || pharmacy.getPendingLicenseFileId().isBlank()) {
                telegramClient.sendMessage(chatId, "⚠️ Pending license file not found.");
                return;
            }

            String caption = "📎 <b>Pending License</b>\n\n"
                    + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                    + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                    + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

            telegramClient.sendDocument(chatId, pharmacy.getPendingLicenseFileId(), caption);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
    }

    private Long parseEntityId(String value) {
        String token = value == null ? "" : value.trim();
        if (token.startsWith("#")) {
            token = token.substring(1).trim();
        }

        try {
            return Long.parseLong(token);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean handleAdminCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.equals("aib_sm")) {
            telegramClient.answerCallback(callbackId);
            sendAdminInboxSummary(chatId);
            return true;
        }
if (data.startsWith("aib_ls_")) {
            telegramClient.answerCallback(callbackId);
            String filter = data.substring("aib_ls_".length());
            adminInboxLastListFilter.put(chatId, filter);
            sendAdminInboxList(chatId, filter);
            return true;
        }
if (data.startsWith("aib_op_")) {
            telegramClient.answerCallback(callbackId);
            Long itemId = Long.parseLong(data.substring("aib_op_".length()));
            try {
                AdminInboxItem item = adminInboxService.getById(itemId);
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("aib_ir_")) {
            telegramClient.answerCallback(callbackId);
            Long itemId = Long.parseLong(data.substring("aib_ir_".length()));
            try {
                AdminInboxItem item = adminInboxService.markInReview(itemId);
                recordAudit(chatId, "ISSUE_MARKED_IN_REVIEW", "ADMIN_INBOX", itemId,
                        "Inbox item marked in review");
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("aib_rs_")) {
            telegramClient.answerCallback(callbackId);
            Long itemId = Long.parseLong(data.substring("aib_rs_".length()));
            try {
                AdminInboxItem item = adminInboxService.markResolved(itemId);
                recordAudit(chatId, "ISSUE_MARKED_RESOLVED", "ADMIN_INBOX", itemId,
                        "Inbox item marked resolved");
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.equals("aib_bk")) {
            telegramClient.answerCallback(callbackId);
            String filter = adminInboxLastListFilter.getOrDefault(chatId, "open_all");
            sendAdminInboxList(chatId, filter);
            return true;
        }
if (data.equals("admin_lc_summary")) {
            telegramClient.answerCallback(callbackId);
            sendAdminLicenseComplianceSummary(chatId);
            return true;
        }
if (data.startsWith("admin_lc_cat_")) {
            telegramClient.answerCallback(callbackId);
            String category = data.substring("admin_lc_cat_".length());
            sendAdminLicenseComplianceCategory(chatId, category);
            return true;
        }
if (data.startsWith("admin_lc_open_")) {
            telegramClient.answerCallback(callbackId);

            String payload = data.substring("admin_lc_open_".length());
            int split = payload.lastIndexOf('_');
            if (split <= 0 || split == payload.length() - 1) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid compliance detail callback.");
                return true;
            }

            String category = payload.substring(0, split);
            Long pharmacyId;
            try {
                pharmacyId = Long.parseLong(payload.substring(split + 1));
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid pharmacy id in compliance callback.");
                return true;
            }

            try {
                openAdminLicenseComplianceDetail(chatId, category, pharmacyId);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_view_pending_")) {
            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_lc_view_pending_".length()));

            try {
                Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
                if (pharmacy.getPendingLicenseFileId() == null || pharmacy.getPendingLicenseFileId().isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Pending license file not found.");
                    return true;
                }

                String caption = "📎 <b>Pending License</b>\n\n"
                        + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();
                telegramClient.sendDocument(chatId, pharmacy.getPendingLicenseFileId(), caption);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_notify_")) {
            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_lc_notify_".length()));
            try {
                LicenseComplianceService.ComplianceDetail detail =
                        licenseComplianceService.notifyPharmacy(pharmacyId, chatId);
                recordAudit(chatId, "COMPLIANCE_NOTIFY_SENT", "COMPLIANCE", pharmacyId,
                    "Compliance notice sent to pharmacy");

                if (detail.telegramId() != null) {
                    telegramClient.sendMessage(
                            detail.telegramId(),
                            "📄 <b>License Compliance Notice</b>\n\n"
                                    + "Your pharmacy requires license compliance attention.\n"
                                    + "Please update or verify your license details to avoid interruption.",
                            "HTML"
                    );
                }

                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_suspend_")) {
            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_lc_suspend_".length()));
            try {
                LicenseComplianceService.ComplianceDetail detail =
                        licenseComplianceService.suspendForCompliance(pharmacyId, chatId);
                recordAudit(chatId, "PHARMACY_SUSPENDED", "COMPLIANCE", pharmacyId,
                    "Pharmacy suspended due to compliance action center");

                if (detail.telegramId() != null) {
                    telegramClient.sendMessage(
                            detail.telegramId(),
                            "⛔ Your pharmacy has been suspended due to license compliance.\n"
                                    + "Please submit valid license information for review."
                    );
                }

                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_unsuspend_")) {
            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_lc_unsuspend_".length()));
            try {
                LicenseComplianceService.ComplianceDetail detail =
                        licenseComplianceService.unsuspendForCompliance(pharmacyId, chatId);
                recordAudit(chatId, "PHARMACY_UNSUSPENDED", "COMPLIANCE", pharmacyId,
                    "Pharmacy unsuspended from compliance action center");

                if (detail.telegramId() != null) {
                    telegramClient.sendMessage(
                            detail.telegramId(),
                            "✅ Your pharmacy suspension has been lifted by admin."
                    );
                }

                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_grace_")) {
            telegramClient.answerCallback(callbackId);
            String[] parts = data.split("_");
            if (parts.length < 5) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid grace period callback.");
                return true;
            }

            int days;
            Long pharmacyId;
            try {
                days = Integer.parseInt(parts[3]);
                pharmacyId = Long.parseLong(parts[4]);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid grace period callback values.");
                return true;
            }

            try {
                LicenseComplianceService.ComplianceDetail detail =
                        licenseComplianceService.extendGracePeriod(pharmacyId, days, chatId);
                recordAudit(chatId, "COMPLIANCE_GRACE_EXTENDED", "COMPLIANCE", pharmacyId,
                    "Grace period extended by " + days + " days");

                if (detail.telegramId() != null) {
                    telegramClient.sendMessage(
                            detail.telegramId(),
                            "🗓 Admin extended your license compliance grace period by " + days + " days."
                    );
                }

                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_lc_clear_")) {
            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_lc_clear_".length()));
            try {
                LicenseComplianceService.ComplianceDetail detail =
                        licenseComplianceService.clearComplianceIssue(pharmacyId, chatId);
                recordAudit(chatId, "COMPLIANCE_ISSUE_CLEARED", "COMPLIANCE", pharmacyId,
                    "Compliance issue cleared by admin");

                if (detail.telegramId() != null) {
                    telegramClient.sendMessage(
                            detail.telegramId(),
                            "✅ Your license compliance issue has been cleared by admin."
                    );
                }

                String category = adminLicenseComplianceLastCategory
                        .getOrDefault(chatId, LicenseComplianceService.CATEGORY_EXPIRING_SOON);
                telegramClient.sendAdminLicenseComplianceDetail(chatId, category, detail);
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
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
if (data.startsWith("inv_pres_on_") || data.startsWith("inv_pres_off_")) {

            telegramClient.answerCallback(callbackId);

            boolean requiresPrescription = data.startsWith("inv_pres_on_");
            Long medicineId = Long.parseLong(data.substring(requiresPrescription
                    ? "inv_pres_on_".length()
                    : "inv_pres_off_".length()));

            try {
                PharmacyInventory updated = inventoryService.setRequiresPrescription(
                        chatId,
                        medicineId,
                        requiresPrescription
                );
                telegramClient.sendMessage(chatId,
                        requiresPrescription
                                ? "✅ Medicine marked as prescription required."
                                : "✅ Medicine unmarked as prescription required.");
                telegramClient.sendPharmacyPrescriptionSettingCard(
                        chatId,
                        updated.getId(),
                        updated.getMedicineName(),
                        updated.getQuantity(),
                        updated.getPrice(),
                        updated.getCurrency(),
                        updated.isRequiresPrescription(),
                        false,
                        updated.getPharmacyId()
                );
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("review_pres_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("review_pres_res_".length()));

            try {
                telegramClient.sendPharmacyPrescriptionReviewCard(
                        chatId,
                        prescriptionReviewService.getPrescriptionStatus(reservationId, null, null)
                );
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("pres_files_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("pres_files_res_".length()));

            try {
                sendPrescriptionFilesToPharmacy(chatId, prescriptionReviewService.getPrescriptionStatus(reservationId, null, null));
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("pres_approve_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("pres_approve_res_".length()));

            try {
                PrescriptionStatusResponseDTO response = prescriptionReviewService.reviewPrescription(
                        reservationId,
                        null,
                        chatId,
                        PrescriptionReviewRequestDTO.builder().decision("approve").build()
                );
                telegramClient.sendMessage(chatId, "✅ Prescription approved. You can now approve the reservation.");
                notifyPrescriptionDecision(response, true);

                reservationRepository.findById(reservationId).ifPresent(reservation ->
                        telegramClient.sendPharmacyPendingReservationCard(
                                chatId,
                                reservation.getId(),
                                reservation.getUserId(),
                                reservation.getMedicineName(),
                                reservation.getRequestedQuantity(),
                                reservation.getCustomerPhone(),
                                reservation.getCustomerName(),
                                reservation.getQrToken(),
                                reservation.isPrescriptionRequired(),
                                reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name()
                        )
                );
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("pres_reject_res_")) {

            telegramClient.answerCallback(callbackId);

            Long reservationId = Long.parseLong(data.substring("pres_reject_res_".length()));

            try {
                PrescriptionStatusResponseDTO response = prescriptionReviewService.reviewPrescription(
                        reservationId,
                        null,
                        chatId,
                        PrescriptionReviewRequestDTO.builder()
                                .decision("reject")
                                .rejectionReason("Prescription rejected by pharmacy")
                                .build()
                );
                telegramClient.sendMessage(chatId, "❌ Prescription rejected. The reservation was rejected.");
                notifyPrescriptionDecision(response, false);
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
if (data.startsWith("admin_pharmacy_page_")) {

            telegramClient.answerCallback(callbackId);

            int page = Integer.parseInt(data.substring("admin_pharmacy_page_".length()));
            if (!AdminPharmacyManagementSessionManager.exists(chatId)) {
                AdminPharmacyManagementSessionManager.save(chatId, new AdminPharmacyManagementSession("LIST", null));
            }

            sendAdminPharmacyManagementPageForSession(chatId, page);
            return true;
        }
// ---- Admin Reservation Deep View ----
if (data.startsWith("admin_res_list_")) {
            telegramClient.answerCallback(callbackId);
            // format: admin_res_list_{STATUS}_{page}
            String payload = data.substring("admin_res_list_".length());
            int lastUnderscore = payload.lastIndexOf('_');
            String status = payload.substring(0, lastUnderscore);
            int page = Integer.parseInt(payload.substring(lastUnderscore + 1));
            MedicineReservationStatus resStatus;
            try {
                resStatus = MedicineReservationStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                telegramClient.sendMessage(chatId, "⚠️ Unknown reservation status: " + status);
                return true;
            }
            telegramClient.sendAdminReservationListPage(
                    chatId,
                    adminService.getReservationsByStatusPage(resStatus, page, 10),
                    status,
                    page
            );
            // Track source status so "Back" works from the detail view
            AdminReservationSessionManager.save(chatId, new AdminReservationSession(null, status));
            return true;
        }
if (data.startsWith("admin_res_open_")) {
            telegramClient.answerCallback(callbackId);
            Long reservationId = Long.parseLong(data.substring("admin_res_open_".length()));
            try {
                MedicineReservation res = adminService.getReservation(reservationId);
                String sourceStatus = null;
                if (AdminReservationSessionManager.exists(chatId)) {
                    sourceStatus = AdminReservationSessionManager.get(chatId).getSourceStatus();
                }
                AdminReservationSessionManager.save(chatId,
                        new AdminReservationSession(reservationId, sourceStatus));
                telegramClient.sendAdminReservationDetail(
                        chatId,
                        adminService.buildAdminReservationDetail(reservationId),
                        reservationId,
                        res.getStatus().name(),
                        sourceStatus
                );
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.equals("admin_res_overview")) {
            telegramClient.answerCallback(callbackId);
            AdminReservationSessionManager.remove(chatId);
            telegramClient.sendAdminReservationOversight(
                    chatId,
                    adminService.viewDetailedReservationOversight()
            );
            return true;
        }
if (data.startsWith("admin_res_cancel_")) {
            telegramClient.answerCallback(callbackId);
            Long reservationId = Long.parseLong(data.substring("admin_res_cancel_".length()));
            try {
                MedicineReservation res = adminService.getReservation(reservationId);
                Long userId = res.getUserId();
                adminService.adminCancelReservation(reservationId);
                recordAudit(chatId, "RESERVATION_FORCE_CANCELLED", "RESERVATION", reservationId,
                    "Reservation force-cancelled by admin");
                telegramClient.sendMessage(chatId, "🚫 Reservation #" + reservationId + " cancelled by admin.");
                telegramClient.sendMessage(userId, "🚫 Your reservation #" + reservationId
                        + " has been cancelled by an administrator.\n\n"
                        + "💊 Medicine: " + (res.getMedicineName() == null ? "N/A" : res.getMedicineName())
                        + "\n🔢 Qty: " + res.getRequestedQuantity());
                // Refresh detail
                String sourceStatus = AdminReservationSessionManager.exists(chatId)
                        ? AdminReservationSessionManager.get(chatId).getSourceStatus() : null;
                telegramClient.sendAdminReservationDetail(
                        chatId,
                        adminService.buildAdminReservationDetail(reservationId),
                        reservationId,
                        "CANCELLED",
                        sourceStatus
                );
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_res_expire_")) {
            telegramClient.answerCallback(callbackId);
            Long reservationId = Long.parseLong(data.substring("admin_res_expire_".length()));
            try {
                MedicineReservation res = adminService.getReservation(reservationId);
                Long userId = res.getUserId();
                adminService.adminExpireReservation(reservationId);
                recordAudit(chatId, "RESERVATION_FORCE_EXPIRED", "RESERVATION", reservationId,
                    "Reservation force-expired by admin");
                telegramClient.sendMessage(chatId, "⌛ Reservation #" + reservationId + " force-expired by admin.");
                telegramClient.sendMessage(userId, "⌛ Your reservation #" + reservationId
                        + " has expired.\n\n"
                        + "💊 Medicine: " + (res.getMedicineName() == null ? "N/A" : res.getMedicineName())
                        + "\n🔢 Qty: " + res.getRequestedQuantity());
                String sourceStatus = AdminReservationSessionManager.exists(chatId)
                        ? AdminReservationSessionManager.get(chatId).getSourceStatus() : null;
                telegramClient.sendAdminReservationDetail(
                        chatId,
                        adminService.buildAdminReservationDetail(reservationId),
                        reservationId,
                        "EXPIRED",
                        sourceStatus
                );
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
if (data.startsWith("admin_res_fulfill_")) {
            telegramClient.answerCallback(callbackId);
            Long reservationId = Long.parseLong(data.substring("admin_res_fulfill_".length()));
            try {
                MedicineReservation res = adminService.getReservation(reservationId);
                Long userId = res.getUserId();
                adminService.adminFulfillReservation(reservationId);
                recordAudit(chatId, "RESERVATION_MARKED_FULFILLED", "RESERVATION", reservationId,
                    "Reservation marked fulfilled by admin");
                telegramClient.sendMessage(chatId, "📦 Reservation #" + reservationId + " marked fulfilled by admin.");
                telegramClient.sendMessage(userId, "📦 Your reservation #" + reservationId
                        + " has been marked as fulfilled.\n\n"
                        + "💊 Medicine: " + (res.getMedicineName() == null ? "N/A" : res.getMedicineName())
                        + "\n🔢 Qty: " + res.getRequestedQuantity());
                String sourceStatus = AdminReservationSessionManager.exists(chatId)
                        ? AdminReservationSessionManager.get(chatId).getSourceStatus() : null;
                telegramClient.sendAdminReservationDetail(
                        chatId,
                        adminService.buildAdminReservationDetail(reservationId),
                        reservationId,
                        "FULFILLED",
                        sourceStatus
                );
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            }
            return true;
        }
// ---- End Admin Reservation Deep View ----
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
if (data.startsWith("close_admin_pharmacy_")) {

            telegramClient.answerCallback(callbackId);

            String[] parts = data.split("_");

            if (parts.length < 5) {
                telegramClient.sendMessage(chatId, "⚠️ Invalid close pharmacy data.");
                return true;
            }

            Integer detailMessageId = Integer.parseInt(parts[3]);
            Long pharmacyId = Long.parseLong(parts[4]);

            telegramClient.deleteMessage(chatId, detailMessageId);
            telegramClient.editAdminPharmacySummaryButtonClosed(chatId, messageId, pharmacyId);

            if (AdminViewSessionManager.exists(chatId)) {
                AdminViewSession current = AdminViewSessionManager.get(chatId);

                if ("PHARMACY_MANAGEMENT".equals(current.getType())
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
                    } else if ("PHARMACY_MANAGEMENT".equals(current.getType())) {
                        telegramClient.editAdminPharmacySummaryButtonClosed(
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
                    + "🏙️ <b>City:</b> " + displayLocation(chatId, pharmacy.getCity()) + "\n"
                    + "📍 <b>Area:</b> " + displayLocation(chatId, pharmacy.getArea()) + "\n"
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
                    } else if ("PHARMACY_MANAGEMENT".equals(current.getType())) {
                        telegramClient.editAdminPharmacySummaryButtonClosed(
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
                    + "🏙️ <b>City:</b> " + displayLocation(chatId, reg.getCity()) + "\n"
                    + "📍 <b>Area:</b> " + displayLocation(chatId, reg.getArea()) + "\n"
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
if (data.startsWith("view_admin_pharmacy_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring("view_admin_pharmacy_".length()));
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
                    } else if ("PHARMACY_MANAGEMENT".equals(current.getType())) {
                        telegramClient.editAdminPharmacySummaryButtonClosed(
                                chatId,
                                current.getSummaryMessageId(),
                                current.getTargetId()
                        );
                    }

                    AdminViewSessionManager.remove(chatId);
                }

                Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
                Integer detailMessageId = telegramClient.sendAdminPharmacyManagementDetail(
                        chatId,
                        buildAdminPharmacyManagementDetail(chatId, pharmacy),
                        pharmacyId,
                        adminPharmacyActionLabel(pharmacy),
                        adminPharmacyActionCallback(pharmacy),
                        pharmacy.getLicenseFileId() != null && !pharmacy.getLicenseFileId().isBlank()
                );

                if (detailMessageId == null) {
                    telegramClient.sendMessage(chatId, "⚠️ Could not open pharmacy detail.");
                    return true;
                }

                telegramClient.editAdminPharmacySummaryButtonOpen(
                        chatId,
                        summaryMessageId,
                        pharmacyId,
                        detailMessageId
                );

                AdminViewSessionManager.save(
                        chatId,
                        new AdminViewSession("PHARMACY_MANAGEMENT", pharmacyId, summaryMessageId, detailMessageId)
                );

                return true;

            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("admin_pharmacy_back_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_back_".length()));
            clearAdminPharmacyEditState(chatId);

            if (AdminViewSessionManager.exists(chatId)) {
                AdminViewSession current = AdminViewSessionManager.get(chatId);
                if ("PHARMACY_MANAGEMENT".equals(current.getType()) && pharmacyId.equals(current.getTargetId())) {
                    if (current.getDetailMessageId() != null) {
                        telegramClient.deleteMessage(chatId, current.getDetailMessageId());
                    }
                    telegramClient.editAdminPharmacySummaryButtonClosed(
                            chatId,
                            current.getSummaryMessageId(),
                            current.getTargetId()
                    );
                    AdminViewSessionManager.remove(chatId);
                }
            }

            return true;
        }
if (data.startsWith("admin_pharmacy_edit_menu_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_menu_".length()));
            telegramClient.sendAdminPharmacyEditFieldMenu(chatId, pharmacyId);
            return true;
        }
if (data.startsWith("admin_pharmacy_prescriptions_")) {

        telegramClient.answerCallback(callbackId);

        Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_prescriptions_".length()));
        startAdminInventoryPrescriptionSelectionFlow(chatId, pharmacyId);
        return true;
    }
if (data.startsWith("admin_inv_pres_on_") || data.startsWith("admin_inv_pres_off_")) {

        telegramClient.answerCallback(callbackId);

        boolean requiresPrescription = data.startsWith("admin_inv_pres_on_");
        String payload = data.substring(requiresPrescription
            ? "admin_inv_pres_on_".length()
            : "admin_inv_pres_off_".length());
        int separator = payload.indexOf('_');

        if (separator <= 0 || separator == payload.length() - 1) {
        telegramClient.sendMessage(chatId, "⚠️ Invalid prescription toggle callback.");
        return true;
        }

        Long pharmacyId = Long.parseLong(payload.substring(0, separator));
        Long medicineId = Long.parseLong(payload.substring(separator + 1));

        try {
        PharmacyInventory updated = inventoryService.setRequiresPrescriptionForPharmacy(
            pharmacyId,
            medicineId,
            requiresPrescription
        );
        telegramClient.sendMessage(chatId,
            requiresPrescription
                ? "✅ Medicine marked as prescription required."
                : "✅ Medicine unmarked as prescription required.");
        telegramClient.sendPharmacyPrescriptionSettingCard(
            chatId,
            updated.getId(),
            updated.getMedicineName(),
            updated.getQuantity(),
            updated.getPrice(),
            updated.getCurrency(),
            updated.isRequiresPrescription(),
            true,
            pharmacyId
        );
        } catch (Exception e) {
        telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }
        return true;
    }
if (data.startsWith("admin_pharmacy_edit_field_name_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_field_name_".length()));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.NAME, null)
            );
            telegramClient.sendMessage(chatId, "✏️ Send the new pharmacy name.");
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_field_phone_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_field_phone_".length()));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.PHONE, null)
            );
            telegramClient.sendMessage(chatId, "📞 Send the new pharmacy phone number.");
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_field_landmark_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_field_landmark_".length()));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.LANDMARK, null)
            );
            telegramClient.sendMessage(chatId, "🧭 Send the new landmark text (free text).\nSend '-' to clear.");
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_field_open_time_")
        || data.startsWith("admin_pharmacy_edit_field_close_time_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring(data.lastIndexOf('_') + 1));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.OPEN_TIME, null)
            );
            telegramClient.sendHourPicker(chatId, "🌅 Select opening hour", "admin_open");
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_field_location_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_field_location_".length()));
            telegramClient.sendAdminPharmacyEditLocationModeMenu(chatId, pharmacyId);
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_location_structured_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_location_structured_".length()));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.LOCATION_STRUCTURED, null)
            );
            LocationSelectionSessionManager.start(chatId, LocationFlowType.ADMIN_EDIT_PHARMACY_LOCATION);
            telegramClient.sendRegionKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_location_exact_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_location_exact_".length()));

            AdminPharmacyEditSessionManager.save(
                    chatId,
                    new AdminPharmacyEditSession(pharmacyId, AdminPharmacyEditField.LOCATION_EXACT, null)
            );
            telegramClient.sendMessage(chatId, "📌 Share exact location pin to update pharmacy coordinates.");
            telegramClient.sendExactPharmacyLocationRequest(chatId);
            return true;
        }
if (data.startsWith("admin_pharmacy_edit_field_approval_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_edit_field_approval_".length()));
            telegramClient.sendAdminPharmacyApprovalStateMenu(chatId, pharmacyId);
            return true;
        }
if (data.startsWith("admin_pharmacy_set_state_approved_")
        || data.startsWith("admin_pharmacy_set_state_not_approved_")
        || data.startsWith("admin_pharmacy_set_state_suspended_")) {

            telegramClient.answerCallback(callbackId);
            Long pharmacyId = Long.parseLong(data.substring(data.lastIndexOf('_') + 1));

            try {
                Pharmacy pharmacy;
                if (data.startsWith("admin_pharmacy_set_state_approved_")) {
                    adminService.reactivatePharmacy(pharmacyId);
                    recordAudit(chatId, "PHARMACY_APPROVED", "PHARMACY", pharmacyId,
                            "Approval state set to approved");
                    telegramClient.sendMessage(chatId, "✅ Approval state set to Approved.");
                    pharmacy = adminService.getPharmacy(pharmacyId);
                    if (pharmacy.getTelegramId() != null) {
                        telegramClient.sendMessage(
                                pharmacy.getTelegramId(),
                                "✅ Your pharmacy approval state has been set to Approved by admin."
                        );
                    }
                } else if (data.startsWith("admin_pharmacy_set_state_not_approved_")) {
                    adminService.setPharmacyNotApproved(pharmacyId);
                    recordAudit(chatId, "PHARMACY_REJECTED", "PHARMACY", pharmacyId,
                            "Approval state set to not approved");
                    telegramClient.sendMessage(chatId, "🕒 Approval state set to Not Approved.");
                } else {
                    adminService.suspendPharmacy(pharmacyId);
                    recordAudit(chatId, "PHARMACY_SUSPENDED", "PHARMACY", pharmacyId,
                            "Approval state set to suspended");
                    telegramClient.sendMessage(chatId, "⛔ Approval state set to Suspended.");
                    pharmacy = adminService.getPharmacy(pharmacyId);
                    if (pharmacy.getTelegramId() != null) {
                        telegramClient.sendMessage(
                                pharmacy.getTelegramId(),
                                "⛔ Your pharmacy has been suspended by admin. Please contact support or admin for follow-up."
                        );
                    }
                }

                refreshAdminPharmacyDetail(chatId, pharmacyId, null);
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("admin_pharmacy_view_license_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring("admin_pharmacy_view_license_".length()));

            try {
                Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
                if (pharmacy.getLicenseFileId() == null || pharmacy.getLicenseFileId().isBlank()) {
                    telegramClient.sendMessage(chatId, "⚠️ Active license file not found.");
                    return true;
                }

                String caption = "📄 <b>Current License</b>\n\n"
                        + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + pharmacy.getName() + "\n"
                        + "📞 <b>Phone:</b> " + pharmacy.getPhone() + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                telegramClient.sendDocument(chatId, pharmacy.getLicenseFileId(), caption);
                return true;
            } catch (Exception e) {
                telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                return true;
            }
        }
if (data.startsWith("admin_pharmacy_action_approve_")
        || data.startsWith("admin_pharmacy_action_suspend_")
        || data.startsWith("admin_pharmacy_action_reactivate_")) {

            telegramClient.answerCallback(callbackId);

            Long pharmacyId = Long.parseLong(data.substring(data.lastIndexOf('_') + 1));

            try {
                if (data.startsWith("admin_pharmacy_action_approve_")) {
                    adminService.approvePharmacy(pharmacyId);
                    recordAudit(chatId, "PHARMACY_APPROVED", "PHARMACY", pharmacyId,
                            "Pharmacy approved from management card");
                    telegramClient.sendMessage(chatId, "✅ Pharmacy approved.");
                } else if (data.startsWith("admin_pharmacy_action_suspend_")) {
                    adminService.suspendPharmacy(pharmacyId);
                    recordAudit(chatId, "PHARMACY_SUSPENDED", "PHARMACY", pharmacyId,
                            "Pharmacy suspended from management card");
                    Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
                    telegramClient.sendMessage(chatId, "⛔ Pharmacy suspended.");
                    if (pharmacy.getTelegramId() != null) {
                        telegramClient.sendMessage(
                                pharmacy.getTelegramId(),
                                "⛔ Your pharmacy has been suspended by admin. Please contact support or admin for follow-up."
                        );
                    }
                } else {
                    adminService.reactivatePharmacy(pharmacyId);
                    recordAudit(chatId, "PHARMACY_UNSUSPENDED", "PHARMACY", pharmacyId,
                            "Pharmacy reactivated from management card");
                    Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
                    telegramClient.sendMessage(chatId, "✅ Pharmacy reactivated.");
                    if (pharmacy.getTelegramId() != null) {
                        telegramClient.sendMessage(
                                pharmacy.getTelegramId(),
                                "✅ Your pharmacy has been reactivated by admin. You can continue using the pharmacy features."
                        );
                    }
                }

                refreshAdminPharmacyDetail(chatId, pharmacyId, messageId);
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
                    recordAudit(chatId, "LICENSE_UPDATE_APPROVED", "PHARMACY_TELEGRAM", pharmacyTelegramId,
                            "Pending license update approved");

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
                recordAudit(chatId, "PHARMACY_APPROVED", "REGISTRATION", registrationId,
                        "Registration approved and pharmacy activated");

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

    private boolean handleAdminInboxTextAction(Long chatId, String normalizedText, String originalText) {
        if (normalizedText.equals("🔄 refresh inbox") || normalizedText.equals("⬅️ inbox summary")) {
            adminInboxPendingAction.remove(chatId);
            sendAdminInboxSummary(chatId);
            return true;
        }

        if (normalizedText.equals("🆕 new issues")) {
            adminInboxPendingAction.remove(chatId);
            adminInboxLastListFilter.put(chatId, "new_issue");
            sendAdminInboxList(chatId, "new_issue");
            return true;
        }

        if (normalizedText.equals("🆕 new feedback")) {
            adminInboxPendingAction.remove(chatId);
            adminInboxLastListFilter.put(chatId, "new_feedback");
            sendAdminInboxList(chatId, "new_feedback");
            return true;
        }

        if (normalizedText.equals("📂 all open")) {
            adminInboxPendingAction.remove(chatId);
            adminInboxLastListFilter.put(chatId, "open_all");
            sendAdminInboxList(chatId, "open_all");
            return true;
        }

        if (normalizedText.equals("✅ resolved")) {
            adminInboxPendingAction.remove(chatId);
            adminInboxLastListFilter.put(chatId, "resolved_all");
            sendAdminInboxList(chatId, "resolved_all");
            return true;
        }

        if (normalizedText.equals("📖 open item by id")) {
            adminInboxPendingAction.put(chatId, "open");
            telegramClient.sendMessage(chatId, "🔢 Send the inbox item ID (example: 123 or #123).");
            return true;
        }

        if (normalizedText.equals("🟡 mark in review")) {
            Long currentItemId = adminInboxCurrentItemId.get(chatId);
            if (currentItemId != null) {
                try {
                    AdminInboxItem item = adminInboxService.markInReview(currentItemId);
                    recordAudit(chatId, "ISSUE_MARKED_IN_REVIEW", "ADMIN_INBOX", currentItemId,
                            "Inbox item marked in review");
                    adminInboxCurrentItemId.put(chatId, item.getId());
                    telegramClient.sendAdminInboxDetail(chatId, item);
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                }
            } else {
                adminInboxPendingAction.put(chatId, "in_review");
                telegramClient.sendMessage(chatId, "🔢 Send the inbox item ID to mark as In Review.");
            }
            return true;
        }

        if (normalizedText.equals("✅ mark resolved")) {
            Long currentItemId = adminInboxCurrentItemId.get(chatId);
            if (currentItemId != null) {
                try {
                    AdminInboxItem item = adminInboxService.markResolved(currentItemId);
                    recordAudit(chatId, "ISSUE_MARKED_RESOLVED", "ADMIN_INBOX", currentItemId,
                            "Inbox item marked resolved");
                    adminInboxCurrentItemId.put(chatId, item.getId());
                    telegramClient.sendAdminInboxDetail(chatId, item);
                } catch (Exception e) {
                    telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
                }
            } else {
                adminInboxPendingAction.put(chatId, "resolved");
                telegramClient.sendMessage(chatId, "🔢 Send the inbox item ID to mark as Resolved.");
            }
            return true;
        }

        if (normalizedText.equals("⬅️ back to list")) {
            adminInboxPendingAction.remove(chatId);
            String filter = adminInboxLastListFilter.getOrDefault(chatId, "open_all");
            sendAdminInboxList(chatId, filter);
            return true;
        }

        String pendingAction = adminInboxPendingAction.get(chatId);
        if (pendingAction == null || originalText == null) {
            return false;
        }

        String token = originalText.trim();
        if (token.startsWith("#")) {
            token = token.substring(1).trim();
        }

        Long itemId;
        try {
            itemId = Long.parseLong(token);
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, "⚠️ Please send a valid numeric item ID.");
            return true;
        }

        adminInboxPendingAction.remove(chatId);

        try {
            if (pendingAction.equals("open")) {
                AdminInboxItem item = adminInboxService.getById(itemId);
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
                return true;
            }

            if (pendingAction.equals("in_review")) {
                AdminInboxItem item = adminInboxService.markInReview(itemId);
                recordAudit(chatId, "ISSUE_MARKED_IN_REVIEW", "ADMIN_INBOX", itemId,
                        "Inbox item marked in review by ID");
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
                return true;
            }

            if (pendingAction.equals("resolved")) {
                AdminInboxItem item = adminInboxService.markResolved(itemId);
                recordAudit(chatId, "ISSUE_MARKED_RESOLVED", "ADMIN_INBOX", itemId,
                        "Inbox item marked resolved by ID");
                adminInboxCurrentItemId.put(chatId, item.getId());
                telegramClient.sendAdminInboxDetail(chatId, item);
                return true;
            }
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
            return true;
        }

        return false;
    }


    private boolean handleReserveCallback(Long chatId, Integer messageId, String data, String callbackId) {
if (data.startsWith("reserve_")) {
    String[] parts = data.split("_", 3);

    if (parts.length < 3) {
        telegramClient.answerCallback(callbackId);
        telegramClient.sendMessage(chatId, "⚠️ Invalid reservation data.");
        return true;
    }

    Long pharmacyId = Long.parseLong(parts[1]);
    String medicineName = parts[2];

    redirectReservationToMiniApp(chatId, messageId, callbackId, pharmacyId, medicineName);
    return true;
}
        return false;
    }


    private boolean handleTimeSelectionCallback(Long chatId, Integer messageId, String data, String callbackId) {
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

            if ("admin".equals(mode)) {
                if (!AdminPharmacyEditSessionManager.exists(chatId)) {
                    telegramClient.sendMessage(chatId, "⚠️ Admin edit session not found.");
                    return true;
                }

                AdminPharmacyEditSession editSession = AdminPharmacyEditSessionManager.get(chatId);
                if (editSession == null) {
                    telegramClient.sendMessage(chatId, "⚠️ Admin edit session not found.");
                    return true;
                }

                if ("hour".equals(kind)) {
                    int hour = Integer.parseInt(parts[4]);
                    telegramClient.sendMinutePicker(
                            chatId,
                            "Select " + phase + " minute",
                            "admin_" + phase,
                            hour
                    );
                    return true;
                }

                if ("minute".equals(kind)) {
                    if (parts.length < 6) {
                        telegramClient.sendMessage(chatId, "⚠️ Invalid minute selection.");
                        return true;
                    }

                    String fullTime = parts[4] + ":" + parts[5];

                    if ("open".equals(phase)) {
                        editSession.setOpenTime(fullTime);
                        editSession.setField(AdminPharmacyEditField.CLOSE_TIME);
                        AdminPharmacyEditSessionManager.save(chatId, editSession);
                        telegramClient.sendMessage(chatId, "✅ Opening time selected: " + fullTime);
                        telegramClient.sendHourPicker(chatId, "🌙 Select closing hour", "admin_close");
                        return true;
                    }

                    if ("close".equals(phase)) {
                        java.time.LocalTime open = java.time.LocalTime.parse(editSession.getOpenTime());
                        java.time.LocalTime close = java.time.LocalTime.parse(fullTime);

                        adminService.updatePharmacyHours(editSession.getPharmacyId(), open, close);
                        telegramClient.sendMessage(
                                chatId,
                                "✅ Pharmacy working hours updated\n\nOpen: " + editSession.getOpenTime() + "\nClose: " + fullTime
                        );

                        Long pharmacyId = editSession.getPharmacyId();
                        AdminPharmacyEditSessionManager.remove(chatId);
                        refreshAdminPharmacyDetail(chatId, pharmacyId, null);
                        return true;
                    }
                }
            }
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

    private void sendPrescriptionReviewCards(Long chatId, List<MedicineReservation> reservations) {
        java.util.LinkedHashMap<String, java.util.List<MedicineReservation>> reviewGroups = new java.util.LinkedHashMap<>();
        for (MedicineReservation reservation : reservations) {
            String key = reservation.getReservationGroupId() != null ? reservation.getReservationGroupId() : "solo_" + reservation.getId();
            reviewGroups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(reservation);
        }

        for (java.util.Map.Entry<String, java.util.List<MedicineReservation>> entry : reviewGroups.entrySet()) {
            java.util.List<MedicineReservation> group = entry.getValue();
            PrescriptionStatusResponseDTO status = group.size() == 1 && group.get(0).getReservationGroupId() == null
                    ? prescriptionReviewService.getPrescriptionStatus(group.get(0).getId(), null, null)
                    : prescriptionReviewService.getPrescriptionStatus(null, entry.getKey(), null);
            telegramClient.sendPharmacyPrescriptionReviewCard(chatId, status);
        }
    }

    private void sendPrescriptionFilesToPharmacy(Long chatId, PrescriptionStatusResponseDTO status) {
        if (status == null || status.getFiles() == null || status.getFiles().isEmpty()) {
            telegramClient.sendMessage(chatId, "⚠️ No prescription files have been uploaded yet.");
            return;
        }

        int index = 1;
        for (var fileMeta : status.getFiles()) {
            PrescriptionReviewService.PrescriptionFileContent file = prescriptionReviewService.downloadPrescriptionFile(fileMeta.getPrescriptionId(), chatId);
            String caption = "🧾 Prescription file " + index + " of " + status.getFiles().size()
                    + "\nFile: " + (fileMeta.getOriginalFilename() == null ? "prescription" : fileMeta.getOriginalFilename())
                    + "\nStatus: " + (fileMeta.getReviewStatus() == null ? "PRESCRIPTION_PENDING" : fileMeta.getReviewStatus());
            telegramClient.sendDocumentBytes(
                    chatId,
                    file.fileData(),
                    file.originalFilename() == null ? "prescription" : file.originalFilename(),
                    caption
            );
            index++;
        }
    }

    private void notifyPrescriptionDecision(PrescriptionStatusResponseDTO status, boolean approved) {
        if (status == null || status.getUserId() == null) {
            return;
        }

        StringBuilder message = new StringBuilder();
        if (approved) {
            message.append("✅ Your prescription was approved.\n\n");
            message.append("Your reservation is now ready for pharmacy approval.");
        } else {
            message.append("❌ Your prescription was rejected.\n\n");
            if (status.getRejectionReason() != null && !status.getRejectionReason().isBlank()) {
                message.append("Reason: ").append(status.getRejectionReason()).append("\n\n");
            }
            message.append("Please upload a valid prescription and try again.");
        }

        if (status.getItems() != null && !status.getItems().isEmpty()) {
            message.append("\n\nMedicines:\n");
            for (var item : status.getItems()) {
                message.append("• ").append(item.getMedicineName()).append("\n");
            }
        }

        telegramClient.sendMessage(status.getUserId(), message.toString());
    }

    private boolean handleExpiryPickerCallback(Long chatId, Integer messageId, String data, String callbackId) {
        if (!data.startsWith("expiry:")) return false;

        telegramClient.answerCallback(callbackId);

        if (data.equals("expiry:noop")) return true;

        String[] parts = data.split(":");
        if (parts.length < 3) return true;

        String action = parts[1];

        try {
            switch (action) {
                case "yn" -> {
                    int base = Integer.parseInt(parts[2]);
                    telegramClient.editExpiryYearPicker(chatId, messageId, base);
                }
                case "y" -> {
                    int year = Integer.parseInt(parts[2]);
                    telegramClient.editExpiryMonthPicker(chatId, messageId, year);
                }
                case "m" -> {
                    int year  = Integer.parseInt(parts[2]);
                    int month = Integer.parseInt(parts[3]);
                    telegramClient.editExpiryDayPicker(chatId, messageId, year, month);
                }
                case "dm" -> {
                    int year  = Integer.parseInt(parts[2]);
                    int month = Integer.parseInt(parts[3]);
                    telegramClient.editExpiryDayPicker(chatId, messageId, year, month);
                }
                case "by" -> {
                    int year = Integer.parseInt(parts[2]);
                    telegramClient.editExpiryMonthPicker(chatId, messageId, year);
                }
                case "d" -> {
                    int year  = Integer.parseInt(parts[2]);
                    int month = Integer.parseInt(parts[3]);
                    int day   = Integer.parseInt(parts[4]);

                    LocalDate selected = LocalDate.of(year, month, day);

                    telegramClient.editExpiryConfirmation(chatId, messageId, selected);

                    // Registration flow
                    if (RegistrationSessionManager.exists(chatId)) {
                        RegistrationSession regSession = RegistrationSessionManager.get(chatId);
                        if (regSession.isWaitingForLicenseExpiryDate()) {
                            regSession.setLicenseExpiryDate(selected);
                            regSession.setWaitingForLicenseExpiryDate(false);
                            telegramClient.sendMessage(chatId,
                                    localizationService.text(chatId, "reg_license_step"));
                            return true;
                        }
                    }

                    // License update flow
                    if (UpdateSessionManager.exists(chatId)) {
                        UpdateSession updateSession = UpdateSessionManager.get(chatId);
                        if (updateSession.getField() == UpdateField.LICENSE
                                && updateSession.isWaitingForLicenseExpiryDate()) {
                            String pendingFileId = updateSession.getPendingFileId();
                            if (pendingFileId == null || pendingFileId.isBlank()) {
                                telegramClient.sendMessage(chatId, "📄 Please upload the new license photo");
                                updateSession.setWaitingForLicenseExpiryDate(false);
                                return true;
                            }

                            pharmacyService.savePendingLicenseUpdate(chatId, pendingFileId, selected);

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
                                    + "📅 <b>License Expiry:</b> " + selected + "\n"
                                    + "📌 <b>Latitude:</b> " + pharmacy.getLatitude() + "\n"
                                    + "📌 <b>Longitude:</b> " + pharmacy.getLongitude() + "\n"
                                    + "🆔 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                            telegramClient.sendPhotoWithLicenseUpdateButtons(
                                    ADMIN_CHAT_ID, pendingFileId, caption, chatId);

                            telegramClient.sendMessage(chatId,
                                    localizationService.text(chatId, "license_update_received_pending"));

                            UpdateSessionManager.remove(chatId);
                            restoreKeyboard(chatId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            telegramClient.sendMessage(chatId, WARN_PREFIX + e.getMessage());
        }

        return true;
    }

    private boolean handleDetailViewCallback(Long chatId, Integer messageId, String data, String callbackId) {
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
    boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);
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
    boolean isFavoriteDetails = favoritePharmacyService.isFavorite(chatId, pharmacyId);
    telegramClient.editPharmacyMessageToDetails(
            chatId,
            messageId,
            formatVerifiedPharmacyName(pharmacy),
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
                item == null ? null : item.getId(),
            price,
            pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
                    && !isTemporaryClosureActive(pharmacy)
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
            pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
            lastStockUpdate,
            isFavoriteDetails
    );

    PharmacyDetailViewSessionManager.save(chatId, new PharmacyDetailViewSession(null));

    // 2. filter LAST
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
    boolean isFavorite = favoritePharmacyService.isFavorite(chatId, pharmacyId);
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
            formatVerifiedPharmacyName(pharmacy),
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
                item == null ? null : item.getId(),
            price,
            pharmacy.getOpenTime() != null && pharmacy.getCloseTime() != null
                    && !isTemporaryClosureActive(pharmacy)
                    && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime()),
            pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString(),
                pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString(),
                isTemporaryClosureActive(pharmacy),
                isTemporaryClosureActive(pharmacy) ? pharmacy.getTemporaryClosureReason() : null,
                isFavorite
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
        inventory == null ? null : inventory.getId(),
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

    private boolean isTemporaryClosureActive(Pharmacy pharmacy) {
        if (pharmacy == null || !pharmacy.isTemporarilyClosed()) {
            return false;
        }

        return pharmacy.getTemporaryClosedUntil() == null
                || pharmacy.getTemporaryClosedUntil().isAfter(java.time.LocalDateTime.now());
    }

    private String formatVerifiedPharmacyName(Pharmacy pharmacy) {
        if (pharmacy == null) {
            return "Pharmacy";
        }

        String name = (pharmacy.getName() == null || pharmacy.getName().isBlank())
                ? "Pharmacy"
                : pharmacy.getName().trim();

        if (!isVerifiedPharmacyBadgeEligible(pharmacy)) {
            return name;
        }

        return name + "  ☑️";
    }

    private boolean isVerifiedPharmacyBadgeEligible(Pharmacy pharmacy) {
        if (pharmacy == null) {
            return false;
        }

        boolean licenseApproved = pharmacy.isApproved() && !pharmacy.isLicenseSuspended();

        String phone = pharmacy.getPhone() == null
                ? ""
                : pharmacy.getPhone().replaceAll("\\s+", "");
        boolean phoneConfirmed = phone.matches("^\\+?[0-9]{7,15}$");

        boolean inventoryUpdatedRecently = pharmacy.getLastInventoryUpdate() != null
                && pharmacy.getLastInventoryUpdate().isAfter(LocalDateTime.now().minusHours(72));

        return licenseApproved && phoneConfirmed && inventoryUpdatedRecently;
    }

    private void sendAdminPharmacyManagementPageForSession(Long chatId, int page) {
        AdminPharmacyManagementSession session = AdminPharmacyManagementSessionManager.get(chatId);
        if (session == null) {
            session = new AdminPharmacyManagementSession("LIST", null);
            AdminPharmacyManagementSessionManager.save(chatId, session);
        }

        var pageData = switch (session.getMode()) {
            case "SEARCH_NAME" -> adminService.searchPharmaciesByName(session.getQuery(), page, 10);
            case "SEARCH_PHONE" -> adminService.searchPharmaciesByPhone(session.getQuery(), page, 10);
            case "SEARCH_TELEGRAM" -> adminService.searchPharmaciesByTelegramId(session.getQuery(), page, 10);
            default -> adminService.getPharmacyManagementPage(page, 10);
        };

        telegramClient.sendAdminPharmacyManagementPage(
                chatId,
                pageData,
                page,
                adminPharmacyPageTitle(session)
        );
    }

    private String adminPharmacyPageTitle(AdminPharmacyManagementSession session) {
        if (session == null || session.getMode() == null || "LIST".equals(session.getMode()) || "MENU".equals(session.getMode())) {
            return "🏥 <b>Pharmacy Management</b>";
        }

        String query = session.getQuery() == null ? "" : session.getQuery();
        return switch (session.getMode()) {
            case "SEARCH_NAME" -> "🏥 <b>Search Results</b>\n🔎 Name: <b>" + query + "</b>";
            case "SEARCH_PHONE" -> "🏥 <b>Search Results</b>\n📞 Phone: <b>" + query + "</b>";
            case "SEARCH_TELEGRAM" -> "🏥 <b>Search Results</b>\n👤 Telegram ID: <b>" + query + "</b>";
            default -> "🏥 <b>Pharmacy Management</b>";
        };
    }

    private String buildAdminPharmacyManagementDetail(Long chatId, Pharmacy pharmacy) {
        long inventoryCount = adminService.countInventoryItems(pharmacy.getId());
        long prescriptionRequiredCount = inventoryService.getInventoryByPharmacyId(pharmacy.getId()).stream()
            .filter(PharmacyInventory::isRequiresPrescription)
            .count();
        long pendingReservations = adminService.countReservations(pharmacy.getId(), MedicineReservationStatus.PENDING);
        long approvedReservations = adminService.countReservations(pharmacy.getId(), MedicineReservationStatus.APPROVED);
        long fulfilledReservations = adminService.countReservations(pharmacy.getId(), MedicineReservationStatus.FULFILLED);
        long expiredReservations = adminService.countReservations(pharmacy.getId(), MedicineReservationStatus.EXPIRED);

        return "🏥 <b>Pharmacy Detail</b>\n\n"
                + "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                + "🏥 <b>Name:</b> " + safeAdminValue(pharmacy.getName()) + "\n"
                + "📞 <b>Phone:</b> " + safeAdminValue(pharmacy.getPhone()) + "\n"
                + "👤 <b>Telegram ID:</b> " + safeAdminValue(pharmacy.getTelegramId()) + "\n"
                + "📌 <b>Approval Status:</b> " + adminPharmacyStatus(pharmacy) + "\n"
                + "🏙️ <b>City:</b> " + safeAdminValue(displayLocation(chatId, pharmacy.getCity())) + "\n"
                + "📍 <b>Area:</b> " + safeAdminValue(displayLocation(chatId, pharmacy.getArea())) + "\n"
                + "🧭 <b>Landmark:</b> " + safeAdminValue(pharmacy.getLandmark()) + "\n"
                + "📌 <b>Latitude:</b> " + safeAdminValue(pharmacy.getLatitude()) + "\n"
                + "📌 <b>Longitude:</b> " + safeAdminValue(pharmacy.getLongitude()) + "\n"
                + "🕒 <b>Open:</b> " + safeAdminValue(pharmacy.getOpenTime()) + "\n"
                + "🌙 <b>Close:</b> " + safeAdminValue(pharmacy.getCloseTime()) + "\n"
                + "💊 <b>Inventory Medicines:</b> " + inventoryCount + "\n"
                + "🧾 <b>Prescription Medicines:</b> " + prescriptionRequiredCount + "\n\n"
                + "📦 <b>Reservations</b>\n"
                + "• Pending: " + pendingReservations + "\n"
                + "• Approved: " + approvedReservations + "\n"
                + "• Fulfilled: " + fulfilledReservations + "\n"
                + "• Expired: " + expiredReservations;
    }

    private String adminPharmacyStatus(Pharmacy pharmacy) {
        if (pharmacy.isLicenseSuspended()) {
            return "Suspended";
        }
        if (pharmacy.isApproved()) {
            return "Approved ✅";
        }
        return "Not Approved";
    }

    private String adminPharmacyActionLabel(Pharmacy pharmacy) {
        if (pharmacy.isLicenseSuspended()) {
            return "✅ Reactivate Pharmacy";
        }
        if (pharmacy.isApproved()) {
            return "⛔ Suspend Pharmacy";
        }
        return "✅ Approve Pharmacy";
    }

    private String adminPharmacyActionCallback(Pharmacy pharmacy) {
        if (pharmacy.isLicenseSuspended()) {
            return "admin_pharmacy_action_reactivate_" + pharmacy.getId();
        }
        if (pharmacy.isApproved()) {
            return "admin_pharmacy_action_suspend_" + pharmacy.getId();
        }
        return "admin_pharmacy_action_approve_" + pharmacy.getId();
    }

    private void refreshAdminPharmacyDetail(Long chatId, Long pharmacyId, Integer currentDetailMessageId) {
        if (currentDetailMessageId != null) {
            telegramClient.deleteMessage(chatId, currentDetailMessageId);
        }

        if (!AdminViewSessionManager.exists(chatId)) {
            return;
        }

        AdminViewSession current = AdminViewSessionManager.get(chatId);
        if (!"PHARMACY_MANAGEMENT".equals(current.getType()) || !pharmacyId.equals(current.getTargetId())) {
            return;
        }

        Pharmacy pharmacy = adminService.getPharmacy(pharmacyId);
        Integer newDetailMessageId = telegramClient.sendAdminPharmacyManagementDetail(
                chatId,
                buildAdminPharmacyManagementDetail(chatId, pharmacy),
                pharmacyId,
                adminPharmacyActionLabel(pharmacy),
                adminPharmacyActionCallback(pharmacy),
                pharmacy.getLicenseFileId() != null && !pharmacy.getLicenseFileId().isBlank()
        );

        if (newDetailMessageId == null) {
            telegramClient.editAdminPharmacySummaryButtonClosed(chatId, current.getSummaryMessageId(), pharmacyId);
            AdminViewSessionManager.remove(chatId);
            return;
        }

        telegramClient.editAdminPharmacySummaryButtonOpen(chatId, current.getSummaryMessageId(), pharmacyId, newDetailMessageId);
        AdminViewSessionManager.save(
                chatId,
                new AdminViewSession("PHARMACY_MANAGEMENT", pharmacyId, current.getSummaryMessageId(), newDetailMessageId)
        );
    }

    private void clearCurrentAdminPharmacyDetail(Long chatId) {
        if (!AdminViewSessionManager.exists(chatId)) {
            return;
        }

        AdminViewSession current = AdminViewSessionManager.get(chatId);
        if (!"PHARMACY_MANAGEMENT".equals(current.getType())) {
            return;
        }

        if (current.getDetailMessageId() != null) {
            telegramClient.deleteMessage(chatId, current.getDetailMessageId());
        }
        telegramClient.editAdminPharmacySummaryButtonClosed(chatId, current.getSummaryMessageId(), current.getTargetId());
        AdminViewSessionManager.remove(chatId);
    }

    private void clearAdminPharmacyManagementState(Long chatId) {
        clearCurrentAdminPharmacyDetail(chatId);
        AdminPharmacyManagementSessionManager.remove(chatId);
    }

    private void clearAdminPharmacyEditState(Long chatId) {
        AdminPharmacyEditSessionManager.remove(chatId);
    }

    private String safeAdminValue(Object value) {
        if (value == null) {
            return "N/A";
        }

        String text = value.toString().trim();
        return text.isBlank() ? "N/A" : text;
    }

}