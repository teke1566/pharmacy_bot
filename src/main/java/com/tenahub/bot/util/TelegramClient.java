package com.tenahub.bot.util;

import com.tenahub.bot.dto.MedicineSuggestionResult;
import com.tenahub.bot.dto.PrescriptionFileMetadataDTO;
import com.tenahub.bot.dto.PrescriptionStatusItemDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.AdminAuditTrail;
import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.registration.RegistrationStep;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramClient {
    private final RestTemplate restTemplate;
    private final LocalizationService localizationService;

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.api-url}")
    private String baseApiUrl;

    @Value("${tenahub.mini-app.base-url}")
    private String miniAppBaseUrl;

    @Value("${tenahub.mini-app.photos-page-path:/?openModal=true&type=pharmacy&pharmacyId={pharmacyId}}")
    private String miniAppPhotosPagePath;

    @Value("${tenahub.mini-app.medicine-photos-page-path:/?openModal=true&type=medicine&medicineId={medicineId}}")
    private String miniAppMedicinePhotosPagePath;

    @Value("${tenahub.mini-app.cart-page-path:/#/cart?mode=cart&confirmAdd=true&pharmacyId={pharmacyId}&medicineId={medicineId}}")
    private String miniAppCartPagePath;

    @Value("${tenahub.mini-app.single-reserve-page-path:/#/cart?mode=single-reserve&pharmacyId={pharmacyId}&medicineId={medicineId}}")
    private String miniAppSingleReservePagePath;

    @Value("${tenahub.mini-app.multi-reserve-page-path:/#/cart?mode=multi-reserve&pharmacyId={pharmacyId}&medicineIds={medicineIds}}")
    private String miniAppMultiReservePagePath;

    @Value("${tenahub.mini-app.pharmacy-pickup-page-path:/#/pickup-scanner?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyPickupPagePath;

    @Value("${tenahub.mini-app.pharmacy-inventory-page-path:/#/pharmacy-inventory?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyInventoryPagePath;

    @Value("${tenahub.mini-app.pharmacy-reservations-page-path:/#/pharmacy-reservations?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyReservationsPagePath;

    @Value("${tenahub.mini-app.pharmacy-prescriptions-page-path:/#/pharmacy-prescriptions?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyPrescriptionsPagePath;

    @Value("${tenahub.mini-app.pharmacy-performance-page-path:/#/pharmacy-performance?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyPerformancePagePath;

    @Value("${tenahub.mini-app.pharmacy-profile-page-path:/#/pharmacy-profile?pharmacyTelegramId={pharmacyTelegramId}}")
    private String miniAppPharmacyProfilePagePath;

    @Value("${tenahub.mini-app.admin-dashboard-page-path:/#/admin?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminDashboardPagePath;

    @Value("${tenahub.mini-app.admin-pharmacies-page-path:/#/admin/pharmacies?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminPharmaciesPagePath;

    @Value("${tenahub.mini-app.admin-reservations-page-path:/#/admin/reservations?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminReservationsPagePath;

    @Value("${tenahub.mini-app.admin-audit-page-path:/#/admin/audit?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminAuditPagePath;

    @Value("${tenahub.mini-app.admin-compliance-page-path:/#/admin/compliance?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminCompliancePagePath;

    @Value("${tenahub.mini-app.admin-feedback-page-path:/#/admin/feedback?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminFeedbackPagePath;

    @Value("${tenahub.mini-app.admin-system-page-path:/#/admin/system?adminTelegramId={adminTelegramId}}")
    private String miniAppAdminSystemPagePath;

    private String apiUrl;

    @PostConstruct
    public void init() {
        this.apiUrl = baseApiUrl + "/bot" + botToken;
        registerBotCommands();
    }

    private String t(Long chatId, String key, Object... args) {
        return localizationService.text(chatId, key, args);
    }

    public String displayMedicine(Long chatId, String medicineName) {
        return MedicineSearchNormalizer.toDisplayName(medicineName, localizationService.getLanguage(chatId));
    }

    public String displayLocation(Long chatId, String locationValue) {
        return EthiopiaLocationTranslator.toDisplayValue(locationValue, localizationService.getLanguage(chatId));
    }

    public String displayLocationAddress(Long chatId, String locationValue) {
        return EthiopiaLocationTranslator.toDisplayAddress(locationValue, localizationService.getLanguage(chatId));
    }

    public String buildMiniAppCartUrl(Long pharmacyId, Long medicineId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (pharmacyId == null || medicineId == null) {
            throw new IllegalArgumentException("pharmacyId and medicineId are required for cart redirect");
        }

        String pathTemplate = (miniAppCartPagePath == null || miniAppCartPagePath.isBlank())
            ? "/#/search?mode=cart&confirmAdd=true&pharmacyId={pharmacyId}&medicineId={medicineId}"
                : miniAppCartPagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{pharmacyId}", String.valueOf(pharmacyId))
                .replace("{medicineId}", String.valueOf(medicineId))
                .replace("{medicineName}", "");

        String finalUrl;

        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            finalUrl = normalizedBase + separator + "startapp=" + appState;
            log.info("Mini app cart URL built: pharmacyId={}, medicineId={}, url={}", pharmacyId, medicineId, finalUrl);
            return finalUrl;
        }

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            finalUrl = resolvedPath;
            log.info("Mini app cart URL built: pharmacyId={}, medicineId={}, url={}", pharmacyId, medicineId, finalUrl);
            return finalUrl;
        }

        if (resolvedPath.startsWith("/")) {
            finalUrl = normalizedBase + resolvedPath;
            log.info("Mini app cart URL built: pharmacyId={}, medicineId={}, url={}", pharmacyId, medicineId, finalUrl);
            return finalUrl;
        }

        finalUrl = normalizedBase + "/" + resolvedPath;
        log.info("Mini app cart URL built: pharmacyId={}, medicineId={}, url={}", pharmacyId, medicineId, finalUrl);
        return finalUrl;
    }

    public String buildMiniAppSingleReserveUrl(Long pharmacyId, Long medicineId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (pharmacyId == null || medicineId == null) {
            throw new IllegalArgumentException("pharmacyId and medicineId are required for single reserve redirect");
        }

        String pathTemplate = (miniAppSingleReservePagePath == null || miniAppSingleReservePagePath.isBlank())
                ? "/#/cart?mode=single-reserve&pharmacyId={pharmacyId}&medicineId={medicineId}"
                : miniAppSingleReservePagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{pharmacyId}", String.valueOf(pharmacyId))
                .replace("{medicineId}", String.valueOf(medicineId));

        return resolveMiniAppUrl(normalizedBase, resolvedPath);
    }

    public String buildMiniAppMultiReserveUrl(Long pharmacyId, List<Long> medicineIds) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (pharmacyId == null) {
            throw new IllegalArgumentException("pharmacyId is required for multi reserve redirect");
        }

        List<Long> sanitizedMedicineIds = medicineIds == null
                ? List.of()
                : medicineIds.stream().filter(id -> id != null && id > 0).distinct().toList();

        if (sanitizedMedicineIds.isEmpty()) {
            throw new IllegalArgumentException("at least one medicineId is required for multi reserve redirect");
        }

        String pathTemplate = (miniAppMultiReservePagePath == null || miniAppMultiReservePagePath.isBlank())
                ? "/#/cart?mode=multi-reserve&pharmacyId={pharmacyId}&medicineIds={medicineIds}"
                : miniAppMultiReservePagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{pharmacyId}", String.valueOf(pharmacyId))
                .replace("{medicineIds}", sanitizedMedicineIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        return resolveMiniAppUrl(normalizedBase, resolvedPath);
    }

    private String resolveMiniAppUrl(String normalizedBase, String resolvedPath) {
        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            return normalizedBase + separator + "startapp=" + appState;
        }

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }

        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }

        return normalizedBase + "/" + resolvedPath;
    }

    public String buildMiniAppSearchUrl() {
        return buildMiniAppSearchUrl(null);
    }

    public String buildMiniAppSearchUrl(String section) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String resolvedPath = "/#/search";
        if (section != null && !section.isBlank()) {
            resolvedPath = resolvedPath + "?section=" + section.trim();
        }

        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            return normalizedBase + separator + "startapp=" + appState;
        }

        return normalizedBase + resolvedPath;
    }

    /**
     * Overflow after a capped reservation card dump: optional "Show 5 more" + Mini App button.
     */
    public void sendReservationListOverflow(Long chatId,
                                            String text,
                                            String showMoreCallbackData,
                                            String miniAppUrl,
                                            String miniAppButtonLabel) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }

        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<Map<String, Object>> row = new ArrayList<>();
            if (showMoreCallbackData != null && !showMoreCallbackData.isBlank()) {
                row.add(Map.of(
                        "text", "Show 5 more",
                        "callback_data", showMoreCallbackData
                ));
            }

            if (miniAppUrl != null && !miniAppUrl.isBlank()) {
                String label = miniAppButtonLabel == null || miniAppButtonLabel.isBlank()
                        ? "Open Mini App"
                        : miniAppButtonLabel;
                if (canUseWebAppButton(miniAppUrl)) {
                    row.add(Map.of("text", label, "web_app", Map.of("url", miniAppUrl)));
                } else if (isValidHttpUrl(miniAppUrl) || miniAppUrl.contains("t.me/")) {
                    row.add(Map.of("text", label, "url", miniAppUrl));
                }
            }

            if (!row.isEmpty()) {
                body.put("reply_markup", Map.of("inline_keyboard", List.of(row)));
            }

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendReservationListOverflow error: chatId={}, error={}", chatId, e.getMessage());
        }
    }

    public String buildMiniAppPharmacyInventoryUrl(Long pharmacyTelegramId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (pharmacyTelegramId == null) {
            throw new IllegalArgumentException("pharmacyTelegramId is required for inventory page");
        }

        String pathTemplate = (miniAppPharmacyInventoryPagePath == null || miniAppPharmacyInventoryPagePath.isBlank())
            ? "/#/pharmacy-inventory?pharmacyTelegramId={pharmacyTelegramId}"
                : miniAppPharmacyInventoryPagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{pharmacyTelegramId}", URLEncoder.encode(String.valueOf(pharmacyTelegramId), StandardCharsets.UTF_8));

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }
        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }
        return normalizedBase + "/" + resolvedPath;
    }

    public void sendPharmacyInventoryMiniAppPrompt(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";
            String inventoryUrl = buildMiniAppPharmacyInventoryUrl(chatId);

            Map<String, Object> button = canUseWebAppButton(inventoryUrl)
                    ? Map.of("text", "📱 Open Inventory App", "web_app", Map.of("url", inventoryUrl))
                    : Map.of("text", "📱 Open Inventory App", "url", inventoryUrl);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📱 Open inventory management in the mini app.");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(button))));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendPharmacyInventoryMiniAppPrompt error: {}", e.getMessage());
        }
    }

    // -------- Reservations Mini App --------

    public String buildMiniAppPharmacyReservationsUrl(Long pharmacyTelegramId) {
        return buildPharmacyMiniAppUrl(miniAppPharmacyReservationsPagePath,
                "/#/pharmacy-reservations?pharmacyTelegramId={pharmacyTelegramId}", pharmacyTelegramId);
    }

    public void sendPharmacyReservationsMiniAppPrompt(Long chatId) {
        sendPharmacyMiniAppPrompt(chatId, buildMiniAppPharmacyReservationsUrl(chatId),
                "📱 Open reservation management in the mini app.", "📱 Open Reservations App",
                "sendPharmacyReservationsMiniAppPrompt");
    }

    // -------- Prescriptions Mini App --------

    public String buildMiniAppPharmacyPrescriptionsUrl(Long pharmacyTelegramId) {
        return buildPharmacyMiniAppUrl(miniAppPharmacyPrescriptionsPagePath,
                "/#/pharmacy-prescriptions?pharmacyTelegramId={pharmacyTelegramId}", pharmacyTelegramId);
    }

    public void sendPharmacyPrescriptionsMiniAppPrompt(Long chatId) {
        sendPharmacyMiniAppPrompt(chatId, buildMiniAppPharmacyPrescriptionsUrl(chatId),
                "📱 Open prescription reviews in the mini app.", "📱 Open Prescriptions App",
                "sendPharmacyPrescriptionsMiniAppPrompt");
    }

    // -------- Performance Mini App --------

    public String buildMiniAppPharmacyPerformanceUrl(Long pharmacyTelegramId) {
        return buildPharmacyMiniAppUrl(miniAppPharmacyPerformancePagePath,
                "/#/pharmacy-performance?pharmacyTelegramId={pharmacyTelegramId}", pharmacyTelegramId);
    }

    public void sendPharmacyPerformanceMiniAppPrompt(Long chatId) {
        sendPharmacyMiniAppPrompt(chatId, buildMiniAppPharmacyPerformanceUrl(chatId),
                "📱 Open performance dashboard in the mini app.", "📱 Open Performance App",
                "sendPharmacyPerformanceMiniAppPrompt");
    }

    // -------- Profile Mini App --------

    public String buildMiniAppPharmacyProfileUrl(Long pharmacyTelegramId) {
        return buildPharmacyMiniAppUrl(miniAppPharmacyProfilePagePath,
                "/#/pharmacy-profile?pharmacyTelegramId={pharmacyTelegramId}", pharmacyTelegramId);
    }

    public void sendPharmacyProfileMiniAppPrompt(Long chatId) {
        sendPharmacyMiniAppPrompt(chatId, buildMiniAppPharmacyProfileUrl(chatId),
                "📱 Open pharmacy profile in the mini app.", "📱 Open Profile App",
                "sendPharmacyProfileMiniAppPrompt");
    }

    // -------- Shared helper for pharmacy mini app prompts --------

    private String buildPharmacyMiniAppUrl(String configuredPath, String defaultPath, Long pharmacyTelegramId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (pharmacyTelegramId == null) {
            throw new IllegalArgumentException("pharmacyTelegramId is required");
        }
        String pathTemplate = (configuredPath == null || configuredPath.isBlank())
                ? defaultPath : configuredPath.trim();
        String resolvedPath = pathTemplate
                .replace("{pharmacyTelegramId}", URLEncoder.encode(String.valueOf(pharmacyTelegramId), StandardCharsets.UTF_8));
        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }
        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }
        return normalizedBase + "/" + resolvedPath;
    }

    private void sendPharmacyMiniAppPrompt(Long chatId, String miniAppUrl, String text, String buttonLabel, String methodName) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> button = canUseWebAppButton(miniAppUrl)
                    ? Map.of("text", buttonLabel, "web_app", Map.of("url", miniAppUrl))
                    : Map.of("text", buttonLabel, "url", miniAppUrl);
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(button))));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("{} error: {}", methodName, e.getMessage());
        }
    }

    // -------- Admin Mini App URL builders + prompts --------

    private String buildAdminMiniAppUrl(String configuredPath, String defaultPath, Long adminTelegramId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (adminTelegramId == null) {
            throw new IllegalArgumentException("adminTelegramId is required");
        }
        String pathTemplate = (configuredPath == null || configuredPath.isBlank())
                ? defaultPath : configuredPath.trim();
        String resolvedPath = pathTemplate
                .replace("{adminTelegramId}", URLEncoder.encode(String.valueOf(adminTelegramId), StandardCharsets.UTF_8));
        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }
        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }
        return normalizedBase + "/" + resolvedPath;
    }

    private void sendAdminMiniAppPrompt(Long chatId, String miniAppUrl, String text, String buttonLabel, String methodName) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> button = canUseWebAppButton(miniAppUrl)
                    ? Map.of("text", buttonLabel, "web_app", Map.of("url", miniAppUrl))
                    : Map.of("text", buttonLabel, "url", miniAppUrl);
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(button))));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("{} error: {}", methodName, e.getMessage());
        }
    }

    public void sendAdminDashboardMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminDashboardPagePath, "/#/admin?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open admin dashboard in the mini app.", "📱 Open Admin Dashboard",
                "sendAdminDashboardMiniAppPrompt");
    }

    public void sendAdminPharmaciesMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminPharmaciesPagePath, "/#/admin/pharmacies?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open pharmacy management in the mini app.", "📱 Open Pharmacy Management",
                "sendAdminPharmaciesMiniAppPrompt");
    }

    public void sendAdminReservationsMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminReservationsPagePath, "/#/admin/reservations?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open reservation oversight in the mini app.", "📱 Open Reservation Oversight",
                "sendAdminReservationsMiniAppPrompt");
    }

    public void sendAdminAuditMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminAuditPagePath, "/#/admin/audit?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open audit trail in the mini app.", "📱 Open Audit Trail",
                "sendAdminAuditMiniAppPrompt");
    }

    public void sendAdminComplianceMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminCompliancePagePath, "/#/admin/compliance?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open license compliance in the mini app.", "📱 Open License Compliance",
                "sendAdminComplianceMiniAppPrompt");
    }

    public void sendAdminFeedbackMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminFeedbackPagePath, "/#/admin/feedback?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open feedback & issues in the mini app.", "📱 Open Feedback & Issues",
                "sendAdminFeedbackMiniAppPrompt");
    }

    public void sendAdminSystemMiniAppPrompt(Long chatId) {
        sendAdminMiniAppPrompt(chatId,
                buildAdminMiniAppUrl(miniAppAdminSystemPagePath, "/#/admin/system?adminTelegramId={adminTelegramId}", chatId),
                "📱 Open system summary in the mini app.", "📱 Open System Summary",
                "sendAdminSystemMiniAppPrompt");
    }

    public String buildMiniAppPharmacyPickupUrl(Long pharmacyTelegramId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (pharmacyTelegramId == null) {
            throw new IllegalArgumentException("pharmacyTelegramId is required for pickup redirect");
        }

        String pathTemplate = (miniAppPharmacyPickupPagePath == null || miniAppPharmacyPickupPagePath.isBlank())
            ? "/#/pickup-scanner?pharmacyTelegramId={pharmacyTelegramId}"
                : miniAppPharmacyPickupPagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{pharmacyTelegramId}", URLEncoder.encode(String.valueOf(pharmacyTelegramId), StandardCharsets.UTF_8));

        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            return normalizedBase + separator + "startapp=" + appState;
        }

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }

        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }

        return normalizedBase + "/" + resolvedPath;
    }

    private void registerBotCommands() {
        try {
            registerBotCommandsForLanguage(null, List.of(
                    Map.of("command", "start", "description", "Reset and show home"),
                    Map.of("command", "miniappsearch", "description", "Mini App Search")
            ));

            registerBotCommandsForLanguage("am", List.of(
                    Map.of("command", "start", "description", "ወደ መነሻ ገጽ ተመለስ"),
                    Map.of("command", "miniappsearch", "description", "Mini App Search")
            ));
        } catch (Exception e) {
            log.warn("registerBotCommands error: {}", e.getMessage());
        }
    }

    private void registerBotCommandsForLanguage(String languageCode, List<Map<String, String>> commands) {
        Map<String, Object> body = new HashMap<>();
        body.put("commands", commands);
        if (languageCode != null && !languageCode.isBlank()) {
            body.put("language_code", languageCode);
        }
        restTemplate.postForObject(apiUrl + "/setMyCommands", body, String.class);
    }

    public void sendMiniAppSearchPrompt(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";
            String searchUrl = buildMiniAppSearchUrl();

            Map<String, Object> button = canUseWebAppButton(searchUrl)
                    ? Map.of("text", "Mini App Search", "web_app", Map.of("url", searchUrl))
                    : Map.of("text", "Mini App Search", "url", searchUrl);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "Open the mini app search page.");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(button))));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendMiniAppSearchPrompt error: {}", e.getMessage());
        }
    }

    public void sendPharmacyPickupScannerPrompt(Long chatId, Long pharmacyTelegramId) {
        try {
            String url = apiUrl + "/sendMessage";
            Long resolvedPharmacyTelegramId = pharmacyTelegramId != null ? pharmacyTelegramId : chatId;
            String pickupUrl = buildMiniAppPharmacyPickupUrl(resolvedPharmacyTelegramId);

            Map<String, Object> button = canUseWebAppButton(pickupUrl)
                    ? Map.of("text", "Open Pickup Scanner", "web_app", Map.of("url", pickupUrl))
                    : Map.of("text", "Open Pickup Scanner", "url", pickupUrl);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "Open the mini app pickup scanner.");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(button))));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendPharmacyPickupScannerPrompt error: {}", e.getMessage());
        }
    }

    public Map<String, Object> miniAppCartButton(Long pharmacyId, Long medicineId) {
        String cartUrl = buildMiniAppCartUrl(pharmacyId, medicineId);
        if (canUseWebAppButton(cartUrl)) {
            return Map.of(
                    "text", "🛒 Reserve in Mini App",
                    "web_app", Map.of("url", cartUrl)
            );
        }

        if (isValidHttpUrl(cartUrl)) {
            return Map.of(
                    "text", "🛒 Reserve in Mini App",
                    "url", cartUrl
            );
        }

        return Map.of(
                "text", "🛒 Reserve in Mini App",
                "url", cartUrl
        );
    }

    public Map<String, Object> miniAppSingleReserveButton(Long chatId, Long pharmacyId, Long medicineId) {
        String reserveUrl = buildMiniAppSingleReserveUrl(pharmacyId, medicineId);
        if (canUseWebAppButton(reserveUrl)) {
            return Map.of(
                    "text", t(chatId, "card_reserve_one_matched_btn"),
                    "web_app", Map.of("url", reserveUrl)
            );
        }

        return Map.of(
                "text", t(chatId, "card_reserve_one_matched_btn"),
                "url", reserveUrl
        );
    }

    public Map<String, Object> miniAppMultiReserveButton(Long chatId, Long pharmacyId, List<Long> medicineIds) {
        String reserveUrl = buildMiniAppMultiReserveUrl(pharmacyId, medicineIds);
        if (canUseWebAppButton(reserveUrl)) {
            return Map.of(
                    "text", t(chatId, "card_multi_reserve_btn"),
                    "web_app", Map.of("url", reserveUrl)
            );
        }

        return Map.of(
                "text", t(chatId, "card_multi_reserve_btn"),
                "url", reserveUrl
        );
    }

    public void sendMiniAppSingleReservePrompt(Long chatId, Long pharmacyId, Long medicineId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🛒 Open the mini app to continue this reservation.");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(miniAppSingleReserveButton(chatId, pharmacyId, medicineId)))));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendMiniAppSingleReservePrompt error: {}", e.getMessage());
        }
    }

    public void sendMiniAppMultiReservePrompt(Long chatId, Long pharmacyId, List<Long> medicineIds) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🧺 Open the mini app to continue the multi-medicine reservation.");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(miniAppMultiReserveButton(chatId, pharmacyId, medicineIds)))));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendMiniAppMultiReservePrompt error: {}", e.getMessage());
        }
    }

    private Map<String, Object> directReserveButton(Long chatId, Long pharmacyId, Long medicineId) {
        String cartUrl = buildMiniAppCartUrl(pharmacyId, medicineId);
        if (canUseWebAppButton(cartUrl)) {
            return Map.of(
                    "text", t(chatId, "card_reserve_btn"),
                    "web_app", Map.of("url", cartUrl)
            );
        }

        return Map.of(
                "text", t(chatId, "card_reserve_btn"),
                "url", cartUrl
        );
    }

    public Map<String, Object> miniAppConfirmOpenButton(Long pharmacyId, Long medicineId) {
        String cartUrl = buildMiniAppCartUrl(pharmacyId, medicineId);
        if (canUseWebAppButton(cartUrl)) {
            return Map.of(
                    "text", "OK",
                    "web_app", Map.of("url", cartUrl)
            );
        }

        return Map.of(
                "text", "OK",
                "url", cartUrl
        );
    }

    public void showMiniAppReserveConfirmation(Long chatId,
                                               Integer messageId,
                                               Long pharmacyId,
                                               String medicineName,
                                               Long medicineId,
                                               Double latitude,
                                               Double longitude,
                                               String phone,
                                               boolean outOfStock,
                                               boolean canRate) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";
            String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

            List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

            List<Map<String, Object>> row1 = new ArrayList<>();
            row1.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
            if (phone != null && !phone.isBlank()) {
                row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
            }
            inlineKeyboard.add(row1);

            List<Map<String, Object>> row2 = new ArrayList<>();
            row2.add(Map.of("text", "Cancel", "callback_data", "cancel_mini_app_reserve_" + pharmacyId + "_" + medicineName));
            if (!outOfStock) {
                row2.add(miniAppConfirmOpenButton(pharmacyId, medicineId));
            }
            inlineKeyboard.add(row2);

            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName)
            ));

            if (canRate) {
                inlineKeyboard.add(List.of(
                        Map.of("text", t(chatId, "card_rate_btn"), "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
                ));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("showMiniAppReserveConfirmation error: {}", e.getMessage());
        }
    }

    public void sendMiniAppCartPrompt(Long chatId, Long pharmacyId, Long medicineId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🛒 Open the mini app to add this medicine to your cart.");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(miniAppCartButton(pharmacyId, medicineId))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("sendMiniAppCartPrompt error: {}", e.getMessage());
        }
    }

    public String canonicalLocationValue(String locationValue) {
        return EthiopiaLocationTranslator.toCanonicalValue(locationValue);
    }

    private String buildMiniAppPhotosUrl(Long pharmacyId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String encodedId = URLEncoder.encode(String.valueOf(pharmacyId), StandardCharsets.UTF_8);
        String pathTemplate = (miniAppPhotosPagePath == null || miniAppPhotosPagePath.isBlank())
            ? "/?openModal=true&type=pharmacy&pharmacyId={pharmacyId}"
                : miniAppPhotosPagePath.trim();
        String resolvedPath = pathTemplate
                .replace("{pharmacyId}", String.valueOf(pharmacyId))
                .replace("{pharmacyIdEncoded}", encodedId);

        // Telegram deep links should pass state in startapp rather than custom path segments.
        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            return normalizedBase + separator + "startapp=" + appState;
        }

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }

        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }

        return normalizedBase + "/" + resolvedPath;
    }

    private String buildMiniAppMedicinePhotosUrl(Long medicineId) {
        String normalizedBase = miniAppBaseUrl == null ? "" : miniAppBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        String medicineIdText = String.valueOf(medicineId);
        String medicineIdEncoded = URLEncoder.encode(medicineIdText, StandardCharsets.UTF_8);

        String pathTemplate = (miniAppMedicinePhotosPagePath == null || miniAppMedicinePhotosPagePath.isBlank())
            ? "/?openModal=true&type=medicine&medicineId={medicineId}"
                : miniAppMedicinePhotosPagePath.trim();

        String resolvedPath = pathTemplate
                .replace("{medicineId}", medicineIdText)
                .replace("{medicineIdEncoded}", medicineIdEncoded);

        if (normalizedBase.contains("t.me/")) {
            String appStatePayload = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            String appState = URLEncoder.encode(appStatePayload, StandardCharsets.UTF_8);
            String separator = normalizedBase.contains("?") ? "&" : "?";
            return normalizedBase + separator + "startapp=" + appState;
        }

        if (resolvedPath.startsWith("http://") || resolvedPath.startsWith("https://")) {
            return resolvedPath;
        }

        if (resolvedPath.startsWith("/")) {
            return normalizedBase + resolvedPath;
        }

        return normalizedBase + "/" + resolvedPath;
    }

    private boolean isValidHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && host != null && !host.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canUseWebAppButton(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null || host.isBlank()) {
                return false;
            }
            return !"t.me".equalsIgnoreCase(host);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> miniAppPhotosButton(Long chatId, Long pharmacyId) {
        String photosUrl = buildMiniAppPhotosUrl(pharmacyId);
        if (canUseWebAppButton(photosUrl)) {
            return Map.of(
                    "text", t(chatId, "card_view_pharmacy_photo_btn"),
                    "web_app", Map.of("url", photosUrl)
            );
        }

        if (isValidHttpUrl(photosUrl)) {
            return Map.of(
                    "text", t(chatId, "card_view_pharmacy_photo_btn"),
                    "url", photosUrl
            );
        }

        return Map.of(
                "text", t(chatId, "card_view_pharmacy_photo_btn"),
                "url", photosUrl
        );
    }

    private Map<String, Object> miniAppMedicinePhotosButton(Long chatId, Long medicineId) {
        String photosUrl = buildMiniAppMedicinePhotosUrl(medicineId);
        if (canUseWebAppButton(photosUrl)) {
            return Map.of(
                    "text", t(chatId, "card_view_medicine_photos_btn"),
                    "web_app", Map.of("url", photosUrl)
            );
        }

        if (isValidHttpUrl(photosUrl)) {
            return Map.of(
                    "text", t(chatId, "card_view_medicine_photos_btn"),
                    "url", photosUrl
            );
        }

        return Map.of(
            "text", t(chatId, "card_view_medicine_photos_btn"),
            "url", photosUrl
        );
    }

    /* ---------------- BASIC SEND ---------------- */

    public void sendMessage(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            String textPreview = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (textPreview.length() > 120) {
                textPreview = textPreview.substring(0, 120) + "...";
            }
            log.warn("Telegram sendMessage error: chatId={}, textPreview={}, error={}", chatId, textPreview, e.getMessage());
        }
    }

    public void sendPendingReservationReminder(Long pharmacyChatId,
                                               MedicineReservation reservation,
                                               long waitingMinutes) {
        if (pharmacyChatId == null || reservation == null) {
            return;
        }

        String message = "⏰ <b>Pending Reservation Reminder</b>\n\n"
                + "Reservation #" + reservation.getId() + "\n"
                + "Medicine: " + safeText(reservation.getMedicineName()) + "\n"
                + "Quantity: " + (reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity()) + "\n"
                + "Customer: " + safeText(reservation.getCustomerName()) + "\n"
                + "Phone: " + safeText(reservation.getCustomerPhone()) + "\n"
                + "Waiting: " + Math.max(0, waitingMinutes) + " minutes\n\n"
                + "Please approve or reject this reservation.";

        sendMessage(pharmacyChatId, message);
    }

    public void sendPendingReservationEscalation(Long pharmacyChatId,
                                                  MedicineReservation reservation,
                                                  long waitingMinutes) {
        if (pharmacyChatId == null || reservation == null) {
            return;
        }

        String message = "⚠️ <b>Reservation Still Pending</b>\n\n"
                + "Reservation #" + reservation.getId() + " has not been acted on in time.\n"
                + "Medicine: " + safeText(reservation.getMedicineName()) + "\n"
                + "Quantity: " + (reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity()) + "\n"
                + "Waiting: " + Math.max(0, waitingMinutes) + " minutes\n\n"
                + "Please take action now.";

        sendMessage(pharmacyChatId, message);
    }

    public void sendPendingReservationEscalationToAdmin(Long adminChatId,
                                                         MedicineReservation reservation,
                                                         Long pharmacyTelegramId,
                                                         long waitingMinutes) {
        if (adminChatId == null || adminChatId <= 0 || reservation == null) {
            return;
        }

        String message = "🚨 <b>Pending Reservation SLA Escalation</b>\n\n"
                + "Reservation #" + reservation.getId() + "\n"
                + "Pharmacy ID: " + reservation.getPharmacyId() + "\n"
                + "Pharmacy Telegram ID: " + (pharmacyTelegramId == null ? "N/A" : pharmacyTelegramId) + "\n"
                + "User ID: " + reservation.getUserId() + "\n"
                + "Medicine: " + safeText(reservation.getMedicineName()) + "\n"
                + "Quantity: " + (reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity()) + "\n"
                + "Waiting: " + Math.max(0, waitingMinutes) + " minutes";

        sendMessage(adminChatId, message);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .trim();
    }

    private void sendMessagePayload(Map<String, Object> body) {
        String url = apiUrl + "/sendMessage";
        try {
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception first) {
            logTelegramSendFailure("html", first);
            if (!body.containsKey("parse_mode")) {
                throw wrapTelegramSendError(first);
            }
            Map<String, Object> fallback = new HashMap<>(body);
            fallback.remove("parse_mode");
            try {
                restTemplate.postForObject(url, fallback, String.class);
                log.warn("Telegram sendMessage succeeded after retry without parse_mode");
            } catch (Exception second) {
                logTelegramSendFailure("plain", second);
                throw wrapTelegramSendError(second);
            }
        }
    }

    private void logTelegramSendFailure(String mode, Exception e) {
        String responseBody = "";
        if (e instanceof HttpStatusCodeException statusEx) {
            responseBody = statusEx.getResponseBodyAsString();
        }
        log.error("Telegram sendMessage failed (mode={}): {} body={}", mode, e.getMessage(), responseBody);
    }

    private RuntimeException wrapTelegramSendError(Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(e);
    }

    public void sendPhoto(Long chatId, String fileId, String caption) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("Telegram sendPhoto error: {}", e.getMessage());
        }
    }

    public void sendDocument(Long chatId, String fileId, String caption) {
        try {
            String url = apiUrl + "/sendDocument";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("document", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            log.warn("Telegram sendDocument error: {}", e.getMessage());
        }
    }

    public void sendLocation(Long chatId, double latitude, double longitude) {
        try {
            String url = apiUrl + "/sendLocation";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("latitude", latitude);
            body.put("longitude", longitude);

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendLocation error: " + e.getMessage());
        }
    }

    public void sendDocumentBytes(Long chatId, byte[] content, String filename, String caption) {
        try {
            String url = apiUrl + "/sendDocument";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", String.valueOf(chatId));
            body.add("caption", caption);

            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            body.add("document", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, requestEntity, String.class);
        } catch (Exception e) {
            System.out.println("sendDocumentBytes error: " + e.getMessage());
        }
    }

    /* ---------------- CALLBACK ---------------- */

    public void answerCallback(String callbackId) {
        try {
            String url = apiUrl + "/answerCallbackQuery";

            Map<String, Object> body = new HashMap<>();
            body.put("callback_query_id", callbackId);

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Callback answer error: " + e.getMessage());
        }
    }

    public void answerCallback(String callbackId, String text, boolean showAlert) {
        try {
            String url = apiUrl + "/answerCallbackQuery";

            Map<String, Object> body = new HashMap<>();
            body.put("callback_query_id", callbackId);
            if (text != null && !text.isBlank()) {
                body.put("text", text);
                body.put("show_alert", showAlert);
            }

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Callback answer text error: " + e.getMessage());
        }
    }

    public void editMessageRemoveButtons(Long chatId, Integer messageId) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", List.of()));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Remove buttons error: " + e.getMessage());
        }
    }


    public void editInlineKeyboard(Long chatId, Integer messageId, List<List<Map<String, Object>>> keyboard) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editInlineKeyboard error: " + e.getMessage());
        }
    }
       private String localizedFilterLabel(Long chatId, String activeFilter) {
        if (activeFilter == null || activeFilter.isBlank()) {
            return t(chatId, "nearest_filter_label");
        }

        return switch (activeFilter.trim().toLowerCase()) {
            case "nearest" -> t(chatId, "nearest_filter_label");
            case "cheapest" -> t(chatId, "cheapest_filter_label");
            case "highest rated" -> t(chatId, "highest_rated_filter_label");
            case "open now" -> t(chatId, "open_now_filter_label");
            case "in stock only" -> t(chatId, "in_stock_only_filter_label");
            default -> activeFilter;
        };
    }
   public Integer sendSearchFilterKeyboardWithMessageId(Long chatId, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

            String nearestText = t(chatId, "nearest_filter_label");
            String cheapestText = t(chatId, "cheapest_filter_label");
            String highestRatedText = t(chatId, "highest_rated_filter_label");
            String openNowText = t(chatId, "open_now_filter_label");
            String inStockText = t(chatId, "in_stock_only_filter_label");

        if (activeFilter != null) {
            String normalized = activeFilter.trim().toLowerCase();

            if (normalized.equals("nearest")) {
                    nearestText = "✅ " + t(chatId, "nearest_filter_label");
            } else if (normalized.equals("cheapest")) {
                    cheapestText = "✅ " + t(chatId, "cheapest_filter_label");
            } else if (normalized.equals("highest rated")) {
                    highestRatedText = "✅ " + t(chatId, "highest_rated_filter_label");
            } else if (normalized.equals("open now")) {
                    openNowText = "✅ " + t(chatId, "open_now_filter_label");
            } else if (normalized.equals("in stock only")) {
                    inStockText = "✅ " + t(chatId, "in_stock_only_filter_label");
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
            body.put("text", t(chatId, "search_filters_title", localizedFilterLabel(chatId, activeFilter)));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📍 " + nearestText),
                        Map.of("text", "💰 " + cheapestText)
                ),
                List.of(
                        Map.of("text", "⭐ " + highestRatedText),
                        Map.of("text", "🟢 " + openNowText)
                ),
                List.of(
                    Map.of("text", "📦 " + inStockText),
                    Map.of("text", t(chatId, "clear_filters_button"))
                ),
                List.of(
                    Map.of("text", t(chatId, "btn_back")),
                    Map.of("text", t(chatId, "btn_home"))
                )
        );

                            body.put("reply_markup", persistentReplyKeyboard(keyboard));
                            body.put("reply_markup", persistentReplyKeyboard(keyboard));

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> result) {
                Object messageIdObj = result.get("message_id");
                if (messageIdObj instanceof Number number) {
                    return number.intValue();
                }
            }
        }
    } catch (Exception e) {
        System.out.println("sendSearchFilterKeyboardWithMessageId error: " + e.getMessage());
    }

    return null;
}
public void sendPharmacyVenue(Long chatId,
                              String pharmacyName,
                              String address,
                              Double latitude,
                              Double longitude) {
    try {
        if (latitude == null || longitude == null) {
            return;
        }

        String url = apiUrl + "/sendVenue";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("title", pharmacyName == null || pharmacyName.isBlank() ? "Pharmacy" : pharmacyName);
        body.put("address", address == null || address.isBlank() ? "Pharmacy location" : address);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyVenue error: " + e.getMessage());
    }
}
    public void editMessageTextWithInlineKeyboard(Long chatId,
                                                  Integer messageId,
                                                  String text,
                                                  List<List<Map<String, Object>>> keyboard) {
        try {
            String url = apiUrl + "/editMessageText";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editMessageTextWithInlineKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- COMMON KEYBOARD HELPERS ---------------- */

    private Map<String, Object> persistentReplyKeyboard(List<List<Map<String, Object>>> keyboard) {
        Map<String, Object> markup = new HashMap<>();
        markup.put("keyboard", keyboard);
        markup.put("resize_keyboard", true);
        markup.put("one_time_keyboard", false);
        markup.put("is_persistent", true);
        return markup;
    }

    private List<List<Map<String, Object>>> buildTwoColumnKeyboard(Long chatId, List<String> values) {
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();

        for (int i = 0; i < values.size(); i += 2) {
            List<Map<String, Object>> row = new ArrayList<>();
            row.add(Map.of("text", displayLocation(chatId, values.get(i))));

            if (i + 1 < values.size()) {
                row.add(Map.of("text", displayLocation(chatId, values.get(i + 1))));
            }

            keyboard.add(row);
        }

        return keyboard;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String capitalizeMedicine(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    /* ---------------- START / DASHBOARD ---------------- */

    public void sendStartKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "👋 Welcome to TenaHub\n\n" +
                    "Find nearby pharmacies instantly.\n\n" +
                    "⚠️ If this is your first time, please share your location using the 📎 attachment button once.\n\n" +
                    "After that you can use the Share Location button below.\n\n" +
                    "🏥 Pharmacy owners can register below."
            );

            Map<String, Object> locationBtn = Map.of(
                    "text", "📍 Share Location",
                    "request_location", true
            );

            Map<String, Object> registerBtn = Map.of(
                    "text", "🏥 Register Pharmacy"
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(locationBtn),
                    List.of(registerBtn)
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendStartKeyboard error: " + e.getMessage());
        }
    }

    public void sendLanguageChooserMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";
            BotLanguage current = localizationService.getLanguage(chatId);

            String enLabel = (current == BotLanguage.ENGLISH ? "✅ " : "") + "🇺🇸 English";
            String amLabel = (current == BotLanguage.AMHARIC ? "✅ " : "") + "🇪🇹 አማርኛ";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", current == BotLanguage.AMHARIC
                    ? "🌐 ቋንቋ ይምረጡ:"
                    : "🌐 Select language:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", enLabel, "callback_data", "set_lang_en"),
                            Map.of("text", amLabel, "callback_data", "set_lang_am")
                    )
            );
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendLanguageChooserMenu error: " + e.getMessage());
        }
    }

    public void sendUserDashboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "dashboard_welcome"));
            body.put("parse_mode", "HTML");

   List<List<Map<String, Object>>> keyboard = List.of(
        List.of(Map.of("text", t(chatId, "search_medicines_button"))),
        List.of(Map.of("text", t(chatId, "search_multiple_meds_button"))),
        List.of(Map.of("text", t(chatId, "my_reservations_button")), Map.of("text", t(chatId, "recent_searches_button"))),
        List.of(Map.of("text", t(chatId, "account_button")), Map.of("text", t(chatId, "my_alerts_button"))),
        List.of(Map.of("text", t(chatId, "share_location_button")), Map.of("text", t(chatId, "register_pharmacy_button"))),
        List.of(Map.of("text", t(chatId, "how_to_use_button")), Map.of("text", t(chatId, "information_button"))),
        List.of(Map.of("text", t(chatId, "leave_feedback_button")), Map.of("text", t(chatId, "language_button")))
);
            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendUserDashboard error: " + e.getMessage());
        }
    }

    public void sendInformationMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "information_menu_title"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "about_tenahub_button"))),
                    List.of(Map.of("text", t(chatId, "contacts_button"))),
                    List.of(Map.of("text", t(chatId, "btn_back")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendInformationMenu error: " + e.getMessage());
        }
    }

    public void sendPharmacyDashboard(Long chatId, String pharmacyName) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🏥 <b>Welcome back, " + pharmacyName + "</b>\n\n" +
                    "Status: Approved ✅\n\n" +
                    "Choose an action below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📦 Inventory")),
                    List.of(Map.of("text", "📦 Reservations")),
                    List.of(Map.of("text", "📊 Performance")),
                    List.of(Map.of("text", "⚙️ Profile")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPharmacyDashboard error: " + e.getMessage());
        }
    }

    public void sendPharmacyPerformanceCard(Long chatId, String cardText) {
        sendMessage(chatId, cardText);
    }

    public void sendPendingPharmacyHome(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "⏳ <b>Your pharmacy registration is under review</b>\n\n" +
                    "Please wait for admin approval."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🔄 Refresh"), Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPendingPharmacyHome error: " + e.getMessage());
        }
    }
public void sendExactPharmacyLocationRequest(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📍 <b>Exact Pharmacy Location</b>\n\n" +
                "Please tap the button below to share the exact pharmacy location."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📍 Send Pharmacy Location", "request_location", true)),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendExactPharmacyLocationRequest error: " + e.getMessage());
    }
}
    public void sendAdminDashboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🛠 <b>Admin Dashboard</b>\n\n" +
                    "Choose an action below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🆕 Pending Registrations")),
                    List.of(Map.of("text", "📄 License Updates")),
                    List.of(Map.of("text", "📄 License Compliance")),
                    List.of(Map.of("text", "🧾 Audit Trail")),
                    List.of(Map.of("text", "🏥 Pharmacy Management")),
                    List.of(Map.of("text", "📦 Reservation Oversight")),
                        List.of(Map.of("text", "📥 Feedback & Issues")),
                    List.of(Map.of("text", "📊 System Summary")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔄 Refresh"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminDashboard error: " + e.getMessage());
        }
    }

    public void sendAdminInboxSummary(Long chatId, long newCount, long inReviewCount, long resolvedCount) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📥 <b>Feedback & Issues Inbox</b>\n\n"
                            + "New: " + newCount + "\n"
                            + "In Review: " + inReviewCount + "\n"
                            + "Resolved: " + resolvedCount + "\n\n"
                        + "Choose a section below.\n"
                        + "To open an item, tap <b>📖 Open Item by ID</b> and send the item number."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🆕 New Issues"), Map.of("text", "🆕 New Feedback")),
                    List.of(Map.of("text", "📂 All Open"), Map.of("text", "✅ Resolved")),
                    List.of(Map.of("text", "📖 Open Item by ID"), Map.of("text", "🔄 Refresh Inbox")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
            );

                body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminInboxSummary error: " + e.getMessage());
        }
    }

    public void sendAdminInboxList(Long chatId, String title, List<AdminInboxItem> items) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);

            StringBuilder text = new StringBuilder();
            text.append("📥 <b>").append(escapeHtml(title)).append("</b>\n\n");

            if (items == null || items.isEmpty()) {
                text.append("No items found.");
                body.put("text", text.toString());
                body.put("parse_mode", "HTML");
                List<List<Map<String, Object>>> keyboard = List.of(
                        List.of(Map.of("text", "🆕 New Issues"), Map.of("text", "🆕 New Feedback")),
                        List.of(Map.of("text", "📂 All Open"), Map.of("text", "✅ Resolved")),
                        List.of(Map.of("text", "📖 Open Item by ID"), Map.of("text", "⬅️ Inbox Summary")),
                        List.of(Map.of("text", "🏠 Home"))
                );
                body.put("reply_markup", persistentReplyKeyboard(keyboard));
                restTemplate.postForObject(url, body, String.class);
                return;
            }

            int limit = Math.min(20, items.size());
            for (int i = 0; i < limit; i++) {
                AdminInboxItem item = items.get(i);
                text.append("#")
                        .append(item.getId())
                        .append(" • ")
                        .append(item.getType())
                        .append(" • ")
                        .append(item.getStatus())
                        .append(" • User ")
                        .append(item.getUserTelegramId())
                        .append("\n");
            }
            text.append("\nTap <b>📖 Open Item by ID</b> and send one of the IDs above.");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📖 Open Item by ID"), Map.of("text", "⬅️ Inbox Summary")),
                    List.of(Map.of("text", "🆕 New Issues"), Map.of("text", "🆕 New Feedback")),
                    List.of(Map.of("text", "📂 All Open"), Map.of("text", "✅ Resolved")),
                    List.of(Map.of("text", "🏠 Home"))
            );

            body.put("text", text.toString().trim());
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminInboxList error: " + e.getMessage());
        }
    }

    public void sendAdminInboxDetail(Long chatId, AdminInboxItem item) {
        if (item == null) {
            sendMessage(chatId, "⚠️ Inbox item not found.");
            return;
        }

        String created = item.getCreatedAt() == null ? "N/A" : item.getCreatedAt().toString().replace('T', ' ');
        String text = "📝 <b>Issue Detail</b>\n\n"
                + "Type: " + item.getType() + "\n"
                + "Status: " + item.getStatus() + "\n"
                + "User: " + item.getUserTelegramId() + "\n"
                + "Pharmacy: " + (item.getPharmacyId() == null ? "N/A" : item.getPharmacyId()) + "\n"
                + "Medicine: " + (item.getMedicineName() == null || item.getMedicineName().isBlank() ? "N/A" : escapeHtml(item.getMedicineName())) + "\n"
                + "Created: " + created + "\n\n"
                + "Message:\n" + escapeHtml(item.getMessageText() == null ? "N/A" : item.getMessageText());

        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🟡 Mark In Review"), Map.of("text", "✅ Mark Resolved")),
                    List.of(Map.of("text", "⬅️ Back to List"), Map.of("text", "⬅️ Inbox Summary")),
                    List.of(Map.of("text", "📖 Open Item by ID"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminInboxDetail error: " + e.getMessage());
        }
    }

    public void sendAdminLicenseComplianceSummary(Long chatId, LicenseComplianceService.ComplianceSummary summary) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📄 <b>License Compliance Action Center</b>\n\n"
                            + "Expiring Soon: " + summary.expiringSoon() + "\n"
                            + "Expired: " + summary.expired() + "\n"
                            + "Missing License: " + summary.missingLicense() + "\n"
                            + "Pending Review: " + summary.pendingReview() + "\n"
                            + "Suspended: " + summary.suspended() + "\n\n"
                        + "Choose a category below.\n"
                        + "Use <b>📖 Open Pharmacy by ID</b> to open detail cards."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "⏳ Expiring Soon"), Map.of("text", "❌ Expired")),
                    List.of(Map.of("text", "📭 Missing License"), Map.of("text", "🕒 Pending Review")),
                    List.of(Map.of("text", "⛔ Suspended"), Map.of("text", "📖 Open Pharmacy by ID")),
                    List.of(Map.of("text", "🔄 Refresh Compliance"), Map.of("text", "⬅️ Back")),
                    List.of(Map.of("text", "🏠 Home"))
            );

                body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminLicenseComplianceSummary error: " + e.getMessage());
        }
    }

    public void sendAdminLicenseComplianceCategory(Long chatId,
                                                   String categoryTitle,
                                                   String categoryKey,
                                                   List<LicenseComplianceService.ComplianceListItem> items) {
        try {
            String url = apiUrl + "/sendMessage";

            if (items == null || items.isEmpty()) {
                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", "📄 <b>" + safe(categoryTitle) + "</b>\n\nNo pharmacies found.");
                body.put("parse_mode", "HTML");
                List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📖 Open Pharmacy by ID"), Map.of("text", "⬅️ Compliance Summary")),
                    List.of(Map.of("text", "🏠 Home"))
                );
                body.put("reply_markup", persistentReplyKeyboard(keyboard));
                restTemplate.postForObject(url, body, String.class);
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                StringBuilder text = new StringBuilder();
                text.append("📄 <b>").append(safe(categoryTitle)).append("</b>\n\n");
                text.append("Pharmacies:\n");

                int limit = Math.min(20, items.size());
                for (int i = 0; i < limit; i++) {
                LicenseComplianceService.ComplianceListItem item = items.get(i);
                String expiry = formatDate(item.licenseExpiryDate());
                text.append("#")
                    .append(item.pharmacyId())
                    .append(" • ")
                    .append(safe(item.pharmacyName()))
                    .append(" • ")
                    .append(safe(item.status()))
                    .append(" • Exp: ")
                    .append(expiry)
                    .append("\n");
                }
                text.append("\nTap <b>📖 Open Pharmacy by ID</b> and send one of the IDs above.");

                body.put("text", text.toString());
            body.put("parse_mode", "HTML");

                List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📖 Open Pharmacy by ID"), Map.of("text", "⬅️ Compliance Summary")),
                    List.of(Map.of("text", "⏳ Expiring Soon"), Map.of("text", "❌ Expired")),
                    List.of(Map.of("text", "📭 Missing License"), Map.of("text", "🕒 Pending Review")),
                    List.of(Map.of("text", "⛔ Suspended"), Map.of("text", "🏠 Home"))
                );
                body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminLicenseComplianceCategory error: " + e.getMessage());
        }
    }

    public void sendAdminLicenseComplianceDetail(Long chatId,
                                                 String categoryKey,
                                                 LicenseComplianceService.ComplianceDetail detail) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);

            String text = "🏥 <b>Pharmacy Compliance Detail</b>\n\n"
                    + "Name: " + safe(detail.pharmacyName()) + "\n"
                    + "Pharmacy ID: " + (detail.pharmacyId() == null ? "N/A" : detail.pharmacyId()) + "\n"
                    + "Telegram ID: " + (detail.telegramId() == null ? "N/A" : detail.telegramId()) + "\n"
                    + "Phone: " + safe(detail.phone()) + "\n"
                    + "Location: " + safe(detail.city()) + ", " + safe(detail.area()) + "\n"
                    + "License Expiry: " + formatDate(detail.licenseExpiryDate()) + "\n"
                    + "Status: " + safe(detail.status()) + "\n"
                    + "Grace Until: " + formatDate(detail.gracePeriodUntil()) + "\n"
                    + "License File Ref: " + safe(detail.activeLicenseFileId()) + "\n"
                    + "Pending File Ref: " + safe(detail.pendingLicenseFileId()) + "\n"
                    + "Pending Review State: " + safe(detail.licenseUpdateStatus()) + "\n"
                    + "Last Action: " + safe(detail.lastComplianceAction()) + "\n"
                    + "Last Action Time: " + formatDateTime(detail.lastComplianceActionAt());

            body.put("text", text);
            body.put("parse_mode", "HTML");

            String suspendLabel = "Suspended".equalsIgnoreCase(safe(detail.status()))
                    ? "✅ Unsuspend Pharmacy"
                    : "⛔ Suspend Pharmacy";

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(Map.of("text", "📣 Notify Pharmacy"), Map.of("text", "🗓 Extend Grace +7d")));
            keyboard.add(List.of(Map.of("text", suspendLabel), Map.of("text", "✅ Clear Issue")));

            if (detail.activeLicenseFileId() != null && !detail.activeLicenseFileId().isBlank()) {
                keyboard.add(List.of(Map.of("text", "📄 View Active License"), Map.of("text", "📎 View Pending License")));
            } else {
                keyboard.add(List.of(Map.of("text", "📎 View Pending License"), Map.of("text", "📖 Open Pharmacy by ID")));
            }

            keyboard.add(List.of(Map.of("text", "⬅️ Back to Category"), Map.of("text", "⬅️ Compliance Summary")));
            keyboard.add(List.of(Map.of("text", "🔄 Refresh Compliance"), Map.of("text", "🏠 Home")));

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminLicenseComplianceDetail error: " + e.getMessage());
        }
    }

    public void sendAdminAuditTrailMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🧾 <b>Audit Trail</b>\n\n"
                            + "Track who changed what and when.\n"
                            + "Choose a view below."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🕒 Recent Actions")),
                    List.of(Map.of("text", "🏥 Pharmacy Actions"), Map.of("text", "📦 Reservation Actions")),
                    List.of(Map.of("text", "📄 Compliance Actions"), Map.of("text", "📥 Inbox Actions")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminAuditTrailMenu error: " + e.getMessage());
        }
    }

    public void sendAdminAuditTrailList(Long chatId, String title, List<AdminAuditTrail> records) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);

            StringBuilder text = new StringBuilder();
            text.append("🧾 <b>").append(escapeHtml(title)).append("</b>\n\n");

            if (records == null || records.isEmpty()) {
                text.append("No audit records found.");
            } else {
                int limit = Math.min(20, records.size());
                for (int i = 0; i < limit; i++) {
                    AdminAuditTrail item = records.get(i);
                    String target = (item.getTargetEntityType() == null ? "TARGET" : item.getTargetEntityType())
                            + " #" + (item.getTargetEntityId() == null ? "N/A" : item.getTargetEntityId());
                    text.append(i + 1)
                            .append(". ")
                            .append(item.getActionType() == null ? "UNKNOWN" : item.getActionType())
                            .append(" • ")
                            .append(target)
                            .append("\n")
                            .append("   Admin: ")
                            .append(item.getAdminTelegramId() == null ? "N/A" : item.getAdminTelegramId())
                            .append(" • ")
                            .append(formatDateTime(item.getActionTimestamp()))
                            .append("\n");

                    if (item.getDetails() != null && !item.getDetails().isBlank()) {
                        text.append("   Details: ").append(escapeHtml(item.getDetails())).append("\n");
                    }
                }
            }

            body.put("text", text.toString().trim());
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🕒 Recent Actions")),
                    List.of(Map.of("text", "🏥 Pharmacy Actions"), Map.of("text", "📦 Reservation Actions")),
                    List.of(Map.of("text", "📄 Compliance Actions"), Map.of("text", "📥 Inbox Actions")),
                    List.of(Map.of("text", "⬅️ Audit Trail"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminAuditTrailList error: " + e.getMessage());
        }
    }

    private String formatDate(LocalDate value) {
        if (value == null) {
            return "N/A";
        }
        return value.toString();
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "N/A";
        }
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public void sendAdminPharmacyManagementMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🏥 <b>Pharmacy Management</b>\n\n"
                            + "Browse pharmacies or search by name, phone, or Telegram ID."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📋 All Pharmacies")),
                    List.of(Map.of("text", "🔎 Search by Name")),
                    List.of(Map.of("text", "📞 Search by Phone")),
                    List.of(Map.of("text", "🆔 Search by Telegram ID")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyManagementMenu error: " + e.getMessage());
        }
    }

    /* ---------------- LOCATION ---------------- */

    public void sendLocationRequest(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📍 Please share your location to find nearby pharmacies.");

            Map<String, Object> locationBtn = Map.of(
                    "text", t(chatId, "share_location_button"),
                    "request_location", true
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(locationBtn),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                    List.of(Map.of("text", t(chatId, "register_pharmacy_button")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendLocationRequest error: " + e.getMessage());
        }
    }

public void sendLocationChoiceMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", t(chatId, "location_choice_title"));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
            List.of(Map.of("text", t(chatId, "share_exact_pharmacy_location_button"), "request_location", true)),
            List.of(Map.of("text", t(chatId, "paste_google_maps_link_button"))),
            List.of(Map.of("text", t(chatId, "select_ethiopia_region_button"))),
                List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
            List.of(Map.of("text", t(chatId, "btn_cancel")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendLocationChoiceMenu error: " + e.getMessage());
    }
}
public void sendRegionSelectionKeyboard(Long chatId) {
    sendReplyKeyboardText(
            chatId,
            t(chatId, "reg_select_region_title"),
            EthiopiaLocationCatalog.getRegions(),
            2,
            true
    );
}

public void sendCitySelectionKeyboard(Long chatId, String region) {
    sendReplyKeyboardText(
            chatId,
            t(chatId, "reg_select_city_title", region),
            EthiopiaLocationCatalog.getCitiesByRegion(region),
            2,
            true
    );
}

public void sendAddisSubCityKeyboard(Long chatId) {
    sendReplyKeyboardText(
            chatId,
            t(chatId, "reg_select_subcity_addis_title"),
            EthiopiaLocationCatalog.getAddisAbabaSubCities(),
            2,
            true
    );
}

public void sendAreaSelectionKeyboard(Long chatId, String title, List<String> areas) {
    sendReplyKeyboardText(
            chatId,
            title,
            areas,
            2,
            true
    );
}

private void sendReplyKeyboardText(Long chatId,
                                   String text,
                                   List<String> options,
                                   int columns,
                                   boolean includeNav) {
    try {
        String url = apiUrl + "/sendMessage";

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();

        for (String option : options) {
            currentRow.add(Map.of("text", displayLocation(chatId, option)));
            if (currentRow.size() == columns) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        if (includeNav) {
            keyboard.add(List.of(
                Map.of("text", t(chatId, "btn_back")),
                Map.of("text", t(chatId, "btn_main"))
            ));
            keyboard.add(List.of(
                Map.of("text", t(chatId, "btn_cancel"))
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", persistentReplyKeyboard(keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendReplyKeyboardText error: " + e.getMessage());
    }
}
public void sendRegionKeyboard(Long chatId) {
    sendSelectionKeyboard(chatId, t(chatId, "reg_select_region_title"), EthiopiaLocationCatalog.getRegions(), 2);
}



public void sendSubCityKeyboard(Long chatId, String city, List<String> subCities) {
    sendSelectionKeyboard(chatId, t(chatId, "reg_select_subcity_title", displayLocation(chatId, city)), subCities, 2);
}


private void sendSelectionKeyboard(Long chatId, String text, List<String> values, int columns) {
    try {
        String url = apiUrl + "/sendMessage";

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();

        for (String value : values) {
            row.add(Map.of("text", displayLocation(chatId, value)));
            if (row.size() == columns) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        if (!row.isEmpty()) {
            keyboard.add(row);
        }

        keyboard.add(List.of(
            Map.of("text", t(chatId, "btn_back")),
            Map.of("text", t(chatId, "btn_main"))
        ));
        keyboard.add(List.of(
            Map.of("text", t(chatId, "btn_cancel"))
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", persistentReplyKeyboard(keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendSelectionKeyboard error: " + e.getMessage());
    }
}
    public void sendRegionKeyboard(Long chatId, List<String> regions) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, regions);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_region_plain"));
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegionKeyboard error: " + e.getMessage());
        }
    }

    public void sendCityKeyboard(Long chatId, String region, List<String> cities) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, cities);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_city_plain", displayLocation(chatId, region)));
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAreaKeyboard(Long chatId, String city, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, areas);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_area_plain", displayLocation(chatId, city)));
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAreaKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- REGISTRATION FLOW ---------------- */

    public void sendRegistrationStepMessage(Long chatId, String text, RegistrationStep step) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", buildRegistrationReplyKeyboard(chatId, step));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationStepMessage error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRegistrationReplyKeyboard(Long chatId, RegistrationStep step) {
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();

        if (step == RegistrationStep.PHONE) {
            keyboard.add(List.of(
                    Map.of("text", t(chatId, "share_phone_number_button"), "request_contact", true)
            ));
        }

        keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
        keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

        return persistentReplyKeyboard(keyboard);
    }

    public void sendRegistrationNamePrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                t(chatId, "reg_name_prompt"),
                RegistrationStep.NAME
        );
    }

    public void sendRegistrationRegionPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                t(chatId, "reg_region_prompt"),
                RegistrationStep.CITY
        );
    }

    public void sendRegistrationCityPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                t(chatId, "reg_city_prompt"),
                RegistrationStep.CITY
        );
    }

    public void sendRegistrationAreaPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                t(chatId, "reg_area_prompt"),
                RegistrationStep.AREA
        );
    }

    public void sendRegistrationPhonePrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                t(chatId, "reg_phone_prompt"),
                RegistrationStep.PHONE
        );
    }

    public void sendRegistrationLocationChoice(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", t(chatId, "location_choice_title"));
        body.put("parse_mode", "HTML");

        // IMPORTANT:
        // Do NOT use request_location here.
        // First let the user tap the text button,
        // then in handleTextMessage() set exact-location mode,
        // then send a second keyboard that requests location.
        List<List<Map<String, Object>>> keyboard = List.of(
            List.of(Map.of("text", t(chatId, "share_exact_pharmacy_location_button"))),
            List.of(Map.of("text", t(chatId, "paste_google_maps_link_button"))),
            List.of(Map.of("text", t(chatId, "select_ethiopia_region_button"))),
                List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
            List.of(Map.of("text", t(chatId, "btn_cancel")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendRegistrationLocationChoice error: " + e.getMessage());
    }
}
public void sendUserReservationItemReadOnly(Long chatId,
                                            Long reservationId,
                                            Long pharmacyId,
                                            Long medicineId,
                                            String pharmacyName,
                                            String pharmacyAddress,
                                            String medicine,
                                            Integer quantity,
                                            String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🆔 <b>" + t(chatId, "res_card_id_label") + "</b> " + reservationId + "\n" +
            "🏥 <b>" + t(chatId, "res_card_pharmacy_label") + "</b> " + pharmacyName + "\n" +
            "📍 <b>" + t(chatId, "card_address_label") + "</b> " + pharmacyAddress + "\n" +
            "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + displayMedicine(chatId, medicine) + "\n" +
            "🔢 <b>" + t(chatId, "res_card_quantity_label") + "</b> " + quantity + "\n" +
            "📌 <b>" + t(chatId, "card_status_label") + "</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> reserveAgainButton = reserveAgainButton(chatId, reservationId, pharmacyId, medicineId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(List.of(reserveAgainButton)));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemReadOnly error: " + e.getMessage());
    }
}
public void sendPharmacyPendingReservationCard(Long chatId,
                                               Long reservationId,
                                               Long userId,
                                               String medicineName,
                                               Integer quantity,
                                               String customerPhone,
               String customerName,
               String qrToken,
               boolean prescriptionRequired,
               String prescriptionReviewStatus) {
    try {
        String url = apiUrl + "/sendMessage";

    String qrLine = (qrToken != null && !qrToken.isBlank())
        ? "🔐 <b>QR Token:</b> " + qrToken + "\n"
        : "";
    String resolvedPrescriptionStatus = prescriptionRequired
        ? (prescriptionReviewStatus == null ? "UPLOAD_REQUIRED" : prescriptionReviewStatus)
        : null;
    String prescriptionLine = prescriptionRequired
        ? "🧾 <b>Prescription:</b> Required (" + safeText(resolvedPrescriptionStatus) + ")\n"
        : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📦 <b>Pending Reservation</b>\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
        qrLine +
        prescriptionLine +
                "💊 <b>Medicine:</b> " + displayMedicine(chatId, medicineName) + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId
        );
        body.put("parse_mode", "HTML");

    List<List<Map<String, Object>>> keyboard = prescriptionRequired
        ? ("PENDING_REVIEW".equalsIgnoreCase(resolvedPrescriptionStatus)
            ? List.of(
                List.of(
                    Map.of("text", "🧾 Review Prescription", "callback_data", "review_pres_res_" + reservationId)
                ),
                List.of(
                    Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                )
            )
            : "APPROVED".equalsIgnoreCase(resolvedPrescriptionStatus)
                ? List.of(
                    List.of(
                        Map.of("text", "✅ Approve", "callback_data", "approve_res_" + reservationId),
                        Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                    )
                )
                : List.of(
                    List.of(
                        Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                    )
                ))
        : List.of(
        List.of(
            Map.of("text", "✅ Approve", "callback_data", "approve_res_" + reservationId),
            Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
        )
    );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyPendingReservationCard error: " + e.getMessage());
    }
}
public void sendPharmacyApprovedReservationCard(Long chatId,
                                                Long reservationId,
                                                Long userId,
                                                String medicineName,
                                                Integer quantity,
                                                String customerPhone,
                                                String customerName,
                        String holdUntil,
                        String qrToken) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
                ? "⏳ <b>Hold Until:</b> " + holdUntil + "\n"
                : "";
    String qrLine = (qrToken != null && !qrToken.isBlank())
        ? "🔐 <b>QR Token:</b> " + qrToken + "\n"
        : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "✅ <b>Approved Reservation</b>\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
        qrLine +
                "💊 <b>Medicine:</b> " + displayMedicine(chatId, medicineName) + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId + "\n" +
                holdLine
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📦 Fulfilled", "callback_data", "fulfill_res_" + reservationId),
                        Map.of("text", "❌ Cancel", "callback_data", "pharmacy_cancel_res_" + reservationId)
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyApprovedReservationCard error: " + e.getMessage());
    }
}
public void sendPharmacyReservationReadOnlyCard(Long chatId,
                                                String title,
                                                Long reservationId,
                                                Long userId,
                                                String medicineName,
                                                Integer quantity,
                                                String customerPhone,
                                                String customerName,
                                                String status,
                                                String extraLine) {
    try {
        String url = apiUrl + "/sendMessage";

        String extra = (extraLine != null && !extraLine.isBlank())
                ? extraLine + "\n"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                title + "\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "💊 <b>Medicine:</b> " + displayMedicine(chatId, medicineName) + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId + "\n" +
                extra +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyReservationReadOnlyCard error: " + e.getMessage());
    }
}
public void sendUserReservationItemWithCancel(Long chatId,
                                              Long reservationId,
                                              Long pharmacyId,
                                              Long medicineId,
                                              String pharmacyName,
                                              String pharmacyAddress,
                                              String medicine,
                                              Integer quantity,
                                              String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🆔 <b>" + t(chatId, "res_card_id_label") + "</b> " + reservationId + "\n" +
            "🏥 <b>" + t(chatId, "res_card_pharmacy_label") + "</b> " + pharmacyName + "\n" +
            "📍 <b>" + t(chatId, "card_address_label") + "</b> " + pharmacyAddress + "\n" +
            "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + displayMedicine(chatId, medicine) + "\n" +
            "🔢 <b>" + t(chatId, "res_card_quantity_label") + "</b> " + quantity + "\n" +
            "📌 <b>" + t(chatId, "card_status_label") + "</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> cancelButton = new HashMap<>();
        cancelButton.put("text", t(chatId, "cancel_button"));
        cancelButton.put("callback_data", "cancel_res_" + reservationId);

        Map<String, Object> reserveAgainButton = reserveAgainButton(chatId, reservationId, pharmacyId, medicineId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(
            List.of(reserveAgainButton),
            List.of(cancelButton)
        ));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemWithCancel error: " + e.getMessage());
    }
}

public void sendMessage(Long chatId, String text, String parseMode) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", parseMode);

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendMessage error: " + e.getMessage());
    }
}
public void sendAccountOverview(Long chatId, String text) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", t(chatId, "my_reservations_button")), Map.of("text", t(chatId, "share_location_button"))),
                List.of(Map.of("text", t(chatId, "register_pharmacy_button")), Map.of("text", t(chatId, "btn_refresh"))),
                List.of(Map.of("text", t(chatId, "btn_main")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAccountOverview error: " + e.getMessage());
    }
}

public void sendAccountMenu(Long chatId, boolean isRegisteredPharmacy) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⚙️ <b>Account Actions</b>");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard;

        if (isRegisteredPharmacy) {
            keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "my_reservations_button"))),
                    List.of(Map.of("text", t(chatId, "share_location_button")), Map.of("text", t(chatId, "btn_profile"))),
                    List.of(Map.of("text", t(chatId, "btn_main")))
            );
        } else {
          keyboard = List.of(
        List.of(Map.of("text", t(chatId, "my_reservations_button")), Map.of("text", t(chatId, "btn_favorite_pharmacies"))),
        List.of(Map.of("text", t(chatId, "share_location_button")), Map.of("text", t(chatId, "register_pharmacy_button"))),
        List.of(Map.of("text", t(chatId, "btn_main")))
);
        }

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAccountMenu error: " + e.getMessage());
    }
}
    public void sendRegistrationGoogleMapHelp(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                body.put("text", t(chatId, "reg_google_map_help_text"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "share_exact_pharmacy_location_button"))),
                    List.of(Map.of("text", t(chatId, "select_ethiopia_region_button"))),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                    List.of(Map.of("text", t(chatId, "btn_cancel")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationGoogleMapHelp error: " + e.getMessage());
        }
    }

    public void sendRegistrationExactLocationHelp(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                body.put("text", t(chatId, "reg_exact_location_help_text"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "send_pharmacy_location_button"), "request_location", true)),
                    List.of(Map.of("text", t(chatId, "paste_google_maps_link_button"))),
                    List.of(Map.of("text", t(chatId, "select_ethiopia_region_button"))),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                    List.of(Map.of("text", t(chatId, "btn_cancel")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationExactLocationHelp error: " + e.getMessage());
        }
    }

    public void sendEthiopiaRegionKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                body.put("text", t(chatId, "reg_select_region_title"));
            body.put("parse_mode", "HTML");

                List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, EthiopiaLocationCatalog.getRegions());
                keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
                keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendEthiopiaRegionKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaCityKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                body.put("text", t(chatId, "reg_select_city_title", displayLocation(chatId, "Addis Ababa")));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", displayLocation(chatId, "Addis Ababa"))),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                    List.of(Map.of("text", t(chatId, "btn_cancel")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaSubCityKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                body.put("text", t(chatId, "reg_select_subcity_addis_title"));
            body.put("parse_mode", "HTML");

                List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, EthiopiaLocationCatalog.getAddisAbabaSubCities());
                keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
                keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaSubCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaAreaBySubCityKeyboard(Long chatId, String subCity, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, areas);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_area_plain", displayLocation(chatId, subCity)));
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaAreaBySubCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendSimpleRegionCityKeyboard(Long chatId, String region, List<String> cities) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, cities);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_city_plain", displayLocation(chatId, region)));
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSimpleRegionCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendSimpleAreaKeyboard(Long chatId, String city, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(chatId, areas);
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_cancel"))));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "reg_select_area_plain", displayLocation(chatId, city)));
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSimpleAreaKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- MEDICINES ---------------- */

    public void sendMedicinePicker(Long chatId, List<String> selected) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = buildMedicinePickerBody(chatId, selected);
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMedicinePicker error: " + e.getMessage());
        }
    }

    public void editMedicinePicker(Long chatId, Integer messageId, List<String> selected) {
        try {
            String url = apiUrl + "/editMessageText";

            String selectedText = selected == null || selected.isEmpty()
                    ? "None"
                    : selected.stream().map(m -> displayMedicine(chatId, m)).collect(Collectors.joining(", "));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text",
                    "💊 <b>Select medicines</b>\n\n "+
                    "Step 5/7\n\n" +
                    "Selected:\n" + selectedText
            );
            body.put("parse_mode", "HTML");
            body.put("reply_markup", buildMedicineReplyMarkup(chatId, selected));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editMedicinePicker error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildMedicinePickerBody(Long chatId, List<String> selected) {
        String selectedText = selected == null || selected.isEmpty()
                ? "None"
                : selected.stream().map(m -> displayMedicine(chatId, m)).collect(Collectors.joining(", "));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "💊 <b>Select medicines</b>\n\n" +
                "Selected:\n" + selectedText
        );
        body.put("parse_mode", "HTML");
        body.put("reply_markup", buildMedicineReplyMarkup(chatId, selected));

        return body;
    }

        private Map<String, Object> buildMedicineReplyMarkup(Long chatId, List<String> selected) {
        List<String> current = selected == null ? List.of() : selected;

        List<List<Map<String, Object>>> keyboard = List.of(
            List.of(medButton(chatId, "insulin", current), medButton(chatId, "paracetamol", current)),
            List.of(medButton(chatId, "amoxicillin", current), medButton(chatId, "ibuprofen", current)),
            List.of(medButton(chatId, "ceftriaxone", current), medButton(chatId, "metformin", current)),
                List.of(
                        Map.of("text", "➕ Add Custom", "callback_data", "med_custom"),
                        Map.of("text", "✅ Done", "callback_data", "med_done")
                ),
                List.of(
                        Map.of("text", "🗑 Clear", "callback_data", "med_clear"),
                        Map.of("text", "❌ Cancel", "callback_data", "med_cancel")
                )
        );

        return Map.of("inline_keyboard", keyboard);
    }

    private Map<String, Object> medButton(Long chatId, String medicine, List<String> selected) {
        boolean chosen = selected.contains(medicine);

        String label = chosen
                ? "✅ " + displayMedicine(chatId, medicine)
                : displayMedicine(chatId, medicine);

        return Map.of(
                "text", label,
                "callback_data", "med_toggle_" + medicine
        );
    }

            public void sendMedicineSuggestions(Long chatId, MedicineSuggestionResult suggestionResult, String rawInput) {
        try {
            String url = apiUrl + "/sendMessage";
                String canonicalInput = suggestionResult == null
                    ? MedicineSearchNormalizer.normalizeToEnglishCanonical(rawInput)
                    : suggestionResult.canonicalInput();
                List<String> typoSuggestions = suggestionResult == null ? List.of() : suggestionResult.typoSuggestions();
                List<String> alternativeSuggestions = suggestionResult == null ? List.of() : suggestionResult.alternativeSuggestions();

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
                StringBuilder text = new StringBuilder(t(chatId, "medicine_suggestion_picker_title", displayMedicine(chatId, rawInput)));
                if (!alternativeSuggestions.isEmpty()) {
                text.append("\n\n").append(t(chatId, "medicine_suggestion_alternative_hint"));
                }
                body.put("text", text.toString());
            body.put("parse_mode", "HTML");

                List<List<Map<String, String>>> keyboard = typoSuggestions.stream()
                    .map(s -> List.of(
                        Map.of("text", displayMedicine(chatId, s), "callback_data", "med_pick_" + s.toLowerCase())
                    ))
                    .collect(Collectors.toList());

                for (String alternative : alternativeSuggestions) {
                keyboard.add(List.of(
                    Map.of("text", "💡 " + displayMedicine(chatId, alternative), "callback_data", "med_pick_" + alternative.toLowerCase())
                ));
                }

            keyboard.add(List.of(
                    Map.of("text", t(chatId, "medicine_suggestion_use_typed", displayMedicine(chatId, canonicalInput)), "callback_data", "med_pick_" + canonicalInput.toLowerCase())
            ));

            keyboard.add(List.of(
                    Map.of("text", t(chatId, "btn_cancel"), "callback_data", "med_custom_cancel")
            ));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMedicineSuggestions error: " + e.getMessage());
        }
    }

    /* ---------------- HOURS ---------------- */

    public void sendHourPicker(Long chatId, String title, String type) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", title);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "00", "callback_data", "time_" + type + "_hour_00"),
                            Map.of("text", "01", "callback_data", "time_" + type + "_hour_01"),
                            Map.of("text", "02", "callback_data", "time_" + type + "_hour_02"),
                            Map.of("text", "03", "callback_data", "time_" + type + "_hour_03")
                    ),
                    List.of(
                            Map.of("text", "04", "callback_data", "time_" + type + "_hour_04"),
                            Map.of("text", "05", "callback_data", "time_" + type + "_hour_05"),
                            Map.of("text", "06", "callback_data", "time_" + type + "_hour_06"),
                            Map.of("text", "07", "callback_data", "time_" + type + "_hour_07")
                    ),
                    List.of(
                            Map.of("text", "08", "callback_data", "time_" + type + "_hour_08"),
                            Map.of("text", "09", "callback_data", "time_" + type + "_hour_09"),
                            Map.of("text", "10", "callback_data", "time_" + type + "_hour_10"),
                            Map.of("text", "11", "callback_data", "time_" + type + "_hour_11")
                    ),
                    List.of(
                            Map.of("text", "12", "callback_data", "time_" + type + "_hour_12"),
                            Map.of("text", "13", "callback_data", "time_" + type + "_hour_13"),
                            Map.of("text", "14", "callback_data", "time_" + type + "_hour_14"),
                            Map.of("text", "15", "callback_data", "time_" + type + "_hour_15")
                    ),
                    List.of(
                            Map.of("text", "16", "callback_data", "time_" + type + "_hour_16"),
                            Map.of("text", "17", "callback_data", "time_" + type + "_hour_17"),
                            Map.of("text", "18", "callback_data", "time_" + type + "_hour_18"),
                            Map.of("text", "19", "callback_data", "time_" + type + "_hour_19")
                    ),
                    List.of(
                            Map.of("text", "20", "callback_data", "time_" + type + "_hour_20"),
                            Map.of("text", "21", "callback_data", "time_" + type + "_hour_21"),
                            Map.of("text", "22", "callback_data", "time_" + type + "_hour_22"),
                            Map.of("text", "23", "callback_data", "time_" + type + "_hour_23")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendHourPicker error: " + e.getMessage());
        }
    }

    public void sendMinutePicker(Long chatId, String title, String type, int hour) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", title);
            body.put("parse_mode", "HTML");

            String hh = String.format("%02d", hour);

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "00", "callback_data", "time_" + type + "_minute_" + hh + "_00"),
                            Map.of("text", "15", "callback_data", "time_" + type + "_minute_" + hh + "_15"),
                            Map.of("text", "30", "callback_data", "time_" + type + "_minute_" + hh + "_30"),
                            Map.of("text", "45", "callback_data", "time_" + type + "_minute_" + hh + "_45")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMinutePicker error: " + e.getMessage());
        }
    }
    public void sendUserReservationItem(Long chatId,
                                    Long reservationId,
                                    String medicine,
                                    Integer quantity,
                                    String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🆔 <b>" + t(chatId, "res_card_id_label") + "</b> " + reservationId + "\n" +
            "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + displayMedicine(chatId, medicine) + "\n" +
            "🔢 <b>" + t(chatId, "res_card_quantity_label") + "</b> " + quantity + "\n" +
            "📌 <b>" + t(chatId, "card_status_label") + "</b> " + status
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
            List.of(
                Map.of(
                    "text", t(chatId, "cancel_button"),
                    "callback_data", "user_cancel_res_" + reservationId
                )
            )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItem error: " + e.getMessage());
    }
}

    /* ---------------- RESERVATION ---------------- */

    public void sendPhoneRequestKeyboard(Long chatId, String message) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message);

            Map<String, Object> contactBtn = Map.of(
                    "text", "📱 Share Phone Number",
                    "request_contact", true
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(contactBtn),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPhoneRequestKeyboard error: " + e.getMessage());
        }
    }

    public void sendMessageRemoveKeyboard(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("remove_keyboard", true));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMessageRemoveKeyboard error: " + e.getMessage());
        }
    }

    public void sendReservationSummary(Long chatId, Integer messageId,
                                       String medicineName, Integer quantity,
                                       String customerName, String customerPhone,
                                       String pharmacyName, String pharmacyPhone,
                                       Double distance, Double latitude, Double longitude,
                                       Long pharmacyId) {
        try {
            String url = messageId != null ? apiUrl + "/editMessageText" : apiUrl + "/sendMessage";

            String summaryText = "📋 <b>Reservation Summary</b>\n\n" +
                    "💊 <b>Medicine:</b> " + displayMedicine(chatId, medicineName) + "\n" +
                    "🔢 <b>Quantity:</b> " + quantity + "\n" +
                    "👤 <b>Name:</b> " + customerName + "\n" +
                    "📞 <b>Phone:</b> " + customerPhone + "\n";

            Map<String, Object> body = new HashMap<>();
            if (messageId != null) {
                body.put("message_id", messageId);
            }
            body.put("chat_id", chatId);
            body.put("text", summaryText);
            body.put("parse_mode", "HTML");

            String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

            List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

            List<Map<String, Object>> row1 = new ArrayList<>();
            row1.add(Map.of("text", "🗺️ Navigate", "url", navigateUrl));
            if (pharmacyPhone != null && !pharmacyPhone.isBlank()) {
                row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId + "_" + medicineName));
            }
            inlineKeyboard.add(row1);

            List<Map<String, Object>> row2 = new ArrayList<>();
            row2.add(Map.of("text", "✅ Confirm", "callback_data", "confirm_reservation_" + pharmacyId));
            inlineKeyboard.add(row2);

            List<Map<String, Object>> row3 = new ArrayList<>();
            row3.add(Map.of("text", "✏️ Edit Name", "callback_data", "edit_res_name_" + pharmacyId));
            row3.add(Map.of("text", "✏️ Edit Phone", "callback_data", "edit_res_phone_" + pharmacyId));
            inlineKeyboard.add(row3);

            inlineKeyboard.add(List.of(
                    Map.of("text", "❌ Cancel", "callback_data", "cancel_reservation_" + pharmacyId)
            ));

            body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

            if (messageId != null) {
                restTemplate.postForObject(url, body, String.class);
            } else {
                restTemplate.postForObject(url, body, String.class);
            }

        } catch (Exception e) {
            System.out.println("sendReservationSummary error: " + e.getMessage());
        }
    }

    public void sendReservationQuantityPicker(Long chatId, String medicineName) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📦 <b>Reserve Medicine</b>\n\n" +
                    "💊 Medicine: " + displayMedicine(chatId, medicineName) + "\n\n" +
                    "Select quantity:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "1", "callback_data", "res_qty_1"),
                            Map.of("text", "2", "callback_data", "res_qty_2"),
                            Map.of("text", "3", "callback_data", "res_qty_3")
                    ),
                    List.of(
                            Map.of("text", "5", "callback_data", "res_qty_5"),
                            Map.of("text", "10", "callback_data", "res_qty_10")
                    ),
                    List.of(Map.of("text", t(chatId, "btn_other"), "callback_data", "res_qty_other")),
                    List.of(
                            Map.of("text", t(chatId, "btn_back"), "callback_data", "res_back"),
                            Map.of("text", t(chatId, "btn_main"), "callback_data", "res_main")
                    ),
                    List.of(Map.of("text", t(chatId, "btn_cancel"), "callback_data", "res_cancel"))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendReservationQuantityPicker error: " + e.getMessage());
        }
    }
public void sendReservationRequestToPharmacy(Long pharmacyChatId,
                                             Long reservationId,
                                             Long userId,
                                             String medicineName,
                                             Integer quantity,
                                             String customerPhone,
                                             String customerName,
                                             long pendingTimeoutMinutes) {
    sendReservationRequestToPharmacy(
            pharmacyChatId,
            reservationId,
            userId,
            medicineName,
            quantity,
            customerPhone,
            customerName,
            pendingTimeoutMinutes,
            false
    );
}

public void sendReservationRequestToPharmacy(Long pharmacyChatId,
                                             Long reservationId,
                                             Long userId,
                                             String medicineName,
                                             Integer quantity,
                                             String customerPhone,
                                             String customerName,
                                             long pendingTimeoutMinutes,
                                             boolean awaitingPrescriptionUpload) {
    try {
        System.out.println("SEND RESERVATION -> pharmacyChatId=" + pharmacyChatId
                + ", reservationId=" + reservationId
                + ", medicine=" + medicineName
                + ", awaitingPrescriptionUpload=" + awaitingPrescriptionUpload);

        if (pharmacyChatId == null || pharmacyChatId <= 0) {
            throw new RuntimeException("Invalid pharmacy chat id: " + pharmacyChatId);
        }

        String title = awaitingPrescriptionUpload
                ? "📦 <b>New reservation — waiting for prescription upload</b>"
                : "📦 <b>New Reservation Request</b>";
        String footer = awaitingPrescriptionUpload
                ? "Please wait for the customer to upload a prescription. You will get a review card after upload."
                : "⏱ Auto-cancel if not approved in: " + pendingTimeoutMinutes + " min\n\nChoose an action:";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", pharmacyChatId);
        body.put("text",
                title + "\n\n" +
                "🆔 Reservation ID: " + reservationId + "\n" +
                "💊 Medicine: " + safeText(displayMedicine(pharmacyChatId, medicineName)) + "\n" +
                "🔢 Quantity: " + quantity + "\n" +
                "👤 Full Name: " + safeText(customerName) + "\n" +
                "📱 Phone: " + safeText(customerPhone) + "\n" +
                "👤 User ID: " + userId + "\n" +
                footer
        );
        body.put("parse_mode", "HTML");

        if (!awaitingPrescriptionUpload) {
            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "✅ Approve", "callback_data", "approve_res_" + reservationId),
                            Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                    ),
                    List.of(
                            Map.of("text", "📦 Fulfilled", "callback_data", "fulfill_res_" + reservationId)
                    )
            );
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
        }

        sendMessagePayload(body);

        System.out.println("SEND RESERVATION SUCCESS -> pharmacyChatId=" + pharmacyChatId);

    } catch (Exception e) {
        log.error("sendReservationRequestToPharmacy error: {}", e.getMessage(), e);
        throw wrapTelegramSendError(e);
    }
}
public void sendUserReservationItemReadOnly(Long chatId,
                                            Long reservationId,
                                            Long pharmacyId,
                                            Long medicineId,
                                            String pharmacyName,
                                            String pharmacyAddress,
                                            String medicine,
                                            Integer quantity,
                                            String status,
                                            String holdUntil) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
            ? "⏳ <b>" + t(chatId, "res_hist_hold_until") + ":</b> " + holdUntil + "\n"
            : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🆔 <b>" + t(chatId, "res_card_id_label") + "</b> " + reservationId + "\n" +
            "🏥 <b>" + t(chatId, "res_card_pharmacy_label") + "</b> " + pharmacyName + "\n" +
            "📍 <b>" + t(chatId, "card_address_label") + "</b> " + pharmacyAddress + "\n" +
            "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + medicine + "\n" +
            "🔢 <b>" + t(chatId, "res_card_quantity_label") + "</b> " + quantity + "\n" +
            holdLine +
            "📌 <b>" + t(chatId, "card_status_label") + "</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> reserveAgainButton = reserveAgainButton(chatId, reservationId, pharmacyId, medicineId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(List.of(reserveAgainButton)));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

        } catch (Exception e) {
        System.out.println("sendUserReservationItemReadOnly error: " + e.getMessage());
        }
    }
    public void sendUserReservationItemWithCancel(Long chatId,
                                              Long reservationId,
                                              Long pharmacyId,
                                              Long medicineId,
                                              String pharmacyName,
                                              String pharmacyAddress,
                                              String medicine,
                                              Integer quantity,
                                              String status,
                                              String holdUntil) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
            ? "⏳ <b>" + t(chatId, "res_hist_hold_until") + ":</b> " + holdUntil + "\n"
            : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🆔 <b>" + t(chatId, "res_card_id_label") + "</b> " + reservationId + "\n" +
            "🏥 <b>" + t(chatId, "res_card_pharmacy_label") + "</b> " + pharmacyName + "\n" +
            "📍 <b>" + t(chatId, "card_address_label") + "</b> " + pharmacyAddress + "\n" +
            "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + medicine + "\n" +
            "🔢 <b>" + t(chatId, "res_card_quantity_label") + "</b> " + quantity + "\n" +
            holdLine +
            "📌 <b>" + t(chatId, "card_status_label") + "</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> cancelButton = new HashMap<>();
        cancelButton.put("text", t(chatId, "cancel_button"));
        cancelButton.put("callback_data", "cancel_res_" + reservationId);

        Map<String, Object> reserveAgainButton = reserveAgainButton(chatId, reservationId, pharmacyId, medicineId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(
            List.of(reserveAgainButton),
            List.of(cancelButton)
        ));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemWithCancel error: " + e.getMessage());
    }
}

private Map<String, Object> reserveAgainButton(Long chatId,
                                               Long reservationId,
                                               Long pharmacyId,
                                               Long medicineId) {
    if (pharmacyId != null && medicineId != null) {
        Map<String, Object> button = new HashMap<>(directReserveButton(chatId, pharmacyId, medicineId));
        button.put("text", t(chatId, "res_card_reserve_again_btn"));
        return button;
    }

    Map<String, Object> button = new HashMap<>();
    button.put("text", t(chatId, "res_card_reserve_again_btn"));
    button.put("callback_data", "reserve_again_" + reservationId);
    return button;
}
    public void editReservationToFulfilledOnly(Long chatId, Integer messageId, Long reservationId) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> fulfillBtn = Map.of(
                    "text", "📦 Fulfilled",
                    "callback_data", "fulfill_res_" + reservationId
            );
            Map<String, Object> cancelBtn = Map.of(
                    "text", "❌ Cancel",
                    "callback_data", "pharmacy_cancel_res_" + reservationId
            );

            List<List<Map<String, Object>>> keyboard = List.of(List.of(fulfillBtn, cancelBtn));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editReservationToFulfilledOnly error: " + e.getMessage());
        }
    }

    public void sendReservationManagementMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📦 <b>Reservation Management</b>\n\nChoose an action:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📦 View Reservations")),
                    List.of(Map.of("text", "📦 Pending Reservations")),
                    List.of(Map.of("text", "🧾 Prescription Reviews")),
                    List.of(Map.of("text", "✅ Approved Reservations")),
                    List.of(Map.of("text", "📦 Mark Fulfilled")),
                    List.of(Map.of("text", "📷 Pickup Scanner")),
                    List.of(Map.of("text", "📜 Reservation History")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendReservationManagementMenu error: " + e.getMessage());
        }
    }

    public void sendMyReservationsSectionMenu(Long chatId, String title) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", title);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", t(chatId, "pending_button")),
                            Map.of("text", t(chatId, "ready_button"))
                    ),
                    List.of(
                            Map.of("text", t(chatId, "fulfilled_button")),
                            Map.of("text", t(chatId, "expired_button"))
                    ),
                    List.of(
                            Map.of("text", t(chatId, "cancelled_button")),
                            Map.of("text", t(chatId, "res_section_reserve_latest_btn"))
                    ),
                    List.of(
                            Map.of("text", t(chatId, "btn_home")),
                            Map.of("text", t(chatId, "btn_back"))
                    )
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMyReservationsSectionMenu error: " + e.getMessage());
        }
    }

    /* ---------------- PHARMACY RESULT ---------------- */
public void sendPharmacyResult(Long chatId,
                               String name,
                               String area,
                               String phone,
                               Double distance,
                               Double latitude,
                               Double longitude,
                               Long pharmacyId,
                               Double rating,
                               boolean canRate,
                               boolean isFavorite,
                               Integer stockQuantity,
                               boolean outOfStock,
                               String medicineName,
                               Long medicineId,
                               BigDecimal price,
                               boolean openNow,
                               String openTime,
                               String closeTime,
                               boolean temporarilyClosed,
                               String temporaryClosureReason) {
    try {
        String url = apiUrl + "/sendMessage";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
            ? t(chatId, "card_price_not_set")
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
            : String.format("%.2f", distance) + " " + t(chatId, "card_km_away");

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (temporarilyClosed) {
            String reason = temporaryClosureReason == null ? "" : temporaryClosureReason.trim();
            hoursText = reason.isBlank()
                    ? t(chatId, "card_temporarily_closed")
                    : t(chatId, "card_temporarily_closed_reason", reason);
        } else if (openTime != null && closeTime != null) {
            hoursText = (openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed")) + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed");
        }

        String stockText = outOfStock
                ? t(chatId, "card_out_of_stock")
                : t(chatId, "card_available", stockQuantity == null ? 0 : stockQuantity);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "⭐ " + t(chatId, "card_rating_label") + ": " + ratingText + "/5\n" +
                "💰 " + t(chatId, "card_price_label") + ": " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText
        );
        body.put("parse_mode", "HTML");

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        inlineKeyboard.add(row2);

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyResult error: " + e.getMessage());
    }
}
public void editPharmacyMessageToCompact(Long chatId,
                                         Integer messageId,
                                         String name,
                                         String area,
                                         String phone,
                                         Double distance,
                                         Double latitude,
                                         Double longitude,
                                         Long pharmacyId,
                                         Double rating,
                                         boolean canRate,
                                         Integer stockQuantity,
                                         boolean outOfStock,
                                         String medicineName,
                                         Long medicineId,
                                         BigDecimal price,
                                         boolean openNow,
                                         String openTime,
                                         String closeTime,
                                         boolean temporarilyClosed,
                                         String temporaryClosureReason,
                                         boolean isFavorite) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
            ? t(chatId, "card_price_not_set")
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
            : String.format("%.2f", distance) + " " + t(chatId, "card_km_away");

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (temporarilyClosed) {
            String reason = temporaryClosureReason == null ? "" : temporaryClosureReason.trim();
            hoursText = reason.isBlank()
                    ? t(chatId, "card_temporarily_closed")
                    : t(chatId, "card_temporarily_closed_reason", reason);
        } else if (openTime != null && closeTime != null) {
            hoursText = (openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed")) + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed");
        }

        String stockText = outOfStock
                ? t(chatId, "card_out_of_stock")
                : t(chatId, "card_available", stockQuantity == null ? 0 : stockQuantity);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "⭐ " + t(chatId, "card_rating_label") + ": " + ratingText + "/5\n" +
                "💰 " + t(chatId, "card_price_label") + ": " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText
        );

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        inlineKeyboard.add(row2);

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToCompact error: " + e.getMessage());
    }
}

public void editPharmacyMessageToCompactWithCall(Long chatId,
                                                 Integer messageId,
                                                 String name,
                                                 String area,
                                                 String phone,
                                                 Double distance,
                                                 Double latitude,
                                                 Double longitude,
                                                 Long pharmacyId,
                                                 Double rating,
                                                 boolean canRate,
                                                 Integer stockQuantity,
                                                 boolean outOfStock,
                                                 String medicineName,
                                                 Long medicineId,
                                                 BigDecimal price,
                                                 boolean openNow,
                                                 String openTime,
                                                 String closeTime,
                                                 boolean temporarilyClosed,
                                                 String temporaryClosureReason,
                                                 boolean isFavorite,
                                                 boolean showCopyLine) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
            ? t(chatId, "card_price_not_set")
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
            : String.format("%.2f", distance) + " " + t(chatId, "card_km_away");

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (temporarilyClosed) {
            String reason = temporaryClosureReason == null ? "" : temporaryClosureReason.trim();
            hoursText = reason.isBlank()
                    ? t(chatId, "card_temporarily_closed")
                    : t(chatId, "card_temporarily_closed_reason", reason);
        } else if (openTime != null && closeTime != null) {
            hoursText = (openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed")) + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? t(chatId, "card_open_now") : t(chatId, "card_closed");
        }

        String stockText = outOfStock
                ? t(chatId, "card_out_of_stock")
                : t(chatId, "card_available", stockQuantity == null ? 0 : stockQuantity);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "⭐ " + t(chatId, "card_rating_label") + ": " + ratingText + "/5\n" +
                "💰 " + t(chatId, "card_price_label") + ": " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n" +
                "📞 <b>Call:</b> " + phoneText +
                (showCopyLine ? "\n📋 <b>Copy:</b> " + phoneText : "")
        );

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl)
        ));

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row2.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        List<Map<String, Object>> row3 = new ArrayList<>();
        if (canRate) {
            row3.add(Map.of("text", t(chatId, "card_rate_btn"), "callback_data", "show_rate_" + pharmacyId + "_" + medicineName));
        }
        row3.add(isFavorite
                ? Map.of("text", t(chatId, "card_saved_btn"), "callback_data", "fav_remove_" + pharmacyId)
                : Map.of("text", t(chatId, "card_save_btn"), "callback_data", "fav_add_" + pharmacyId));
        inlineKeyboard.add(row3);

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "card_report_btn"), "callback_data", "report_issue_" + pharmacyId + "_" + medicineName)
        ));

        inlineKeyboard.add(List.of(
            Map.of("text", "🔙 Hide Call", "callback_data", "hide_call_" + pharmacyId + "_" + medicineName)
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageToCompactWithCall error: " + e.getMessage());
    }
}

public void editPharmacyMessageToggleReserve(Long chatId,
                                             Integer messageId,
                                             String name,
                                             String area,
                                             String phone,
                                             Double latitude,
                                             Double longitude,
                                             Long pharmacyId,
                                             Double rating,
                                             boolean canRate,
                                             Integer stockQuantity,
                                             boolean outOfStock,
                                             String medicineName,
                                             BigDecimal price,
                                             boolean openNow,
                                             String openTime,
                                             String closeTime,
                                             boolean reserveOpen) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of(
                    "text", reserveOpen ? t(chatId, "card_close_reserve_btn") : t(chatId, "card_reserve_btn"),
                    "callback_data", reserveOpen
                            ? "close_reserve_" + pharmacyId + "_" + medicineName
                            : "toggle_reserve_" + pharmacyId + "_" + medicineName
            ));
        }

        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (reserveOpen && !outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "1", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_1"),
                    Map.of("text", "2", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_2"),
                    Map.of("text", "3", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_3")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "5", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_5"),
                    Map.of("text", "10", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_10")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "btn_other"), "callback_data", "res_qty_custom_" + pharmacyId + "_" + medicineName)
            ));
        }

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_rate_btn"), "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        inlineKeyboard.add(List.of(
            Map.of("text", t(chatId, "card_report_btn"), "callback_data", "report_issue_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("message is not modified")) {
            System.out.println("Reserve toggle skipped: already in same state.");
            return;
        }
        System.out.println("editPharmacyMessageToggleReserve error: " + e.getMessage());
    }
}

public void editPharmacyMessageIssueMenu(Long chatId,
                                         Integer messageId,
                                         Long pharmacyId,
                                         String medicineName,
                                         Double latitude,
                                         Double longitude,
                                         String phone,
                                         Long medicineId,
                                         boolean outOfStock,
                                         boolean openNow,
                                         boolean isFavorite,
                                         boolean issueMenuOpen) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";
        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        row2.add(Map.of("text", t(chatId, "card_hide_details_btn"), "callback_data", "hide_details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        inlineKeyboard.add(List.of(
                isFavorite
                        ? Map.of("text", t(chatId, "card_saved_btn"), "callback_data", "fav_remove_" + pharmacyId)
                        : Map.of("text", t(chatId, "card_save_btn"), "callback_data", "fav_add_" + pharmacyId)
        ));

        List<Map<String, Object>> row4 = new ArrayList<>();
        row4.add(miniAppPhotosButton(chatId, pharmacyId));
        if (medicineId != null) {
            row4.add(miniAppMedicinePhotosButton(chatId, medicineId));
        }
        inlineKeyboard.add(row4);

        if (issueMenuOpen) {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "issue_menu_title"), "callback_data", "issue_menu_title")
            ));
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "issue_type_stock_short"), "callback_data", "issue_type_stock_" + pharmacyId + "_" + medicineName),
                    Map.of("text", t(chatId, "issue_type_phone_short"), "callback_data", "issue_type_phone_" + pharmacyId + "_" + medicineName)
            ));
            inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_location_short"), "callback_data", "issue_type_location_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "issue_type_service_short"), "callback_data", "issue_type_service_" + pharmacyId + "_" + medicineName)
            ));
            inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_closed_short"), "callback_data", "issue_type_closed_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "issue_type_other"), "callback_data", "issue_type_other_" + pharmacyId + "_" + medicineName)
                ));
                inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "issue_menu_close"), "callback_data", "issue_back_" + pharmacyId + "_" + medicineName)
            ));
        } else {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_report_btn"), "callback_data", "report_issue_" + pharmacyId + "_" + medicineName)
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageIssueMenu error: " + e.getMessage());
    }
}

public void editPharmacyMessageAskCustomQuantity(Long chatId,
                                                 Integer messageId,
                                                 String name,
                                                 String area,
                                                 String phone,
                                                 Double distance,
                                                 Double latitude,
                                                 Double longitude,
                                                 Long pharmacyId,
                                                 Double rating,
                                                 Integer stockQuantity,
                                                 boolean outOfStock,
                                                 String medicineName,
                                                 BigDecimal price,
                                                 boolean openNow,
                                                 String openTime,
                                                 String closeTime) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "✍️ <b>Enter quantity as a number</b>\n" +
                "Example: 4"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName)
        ));
        keyboard.add(List.of(
                Map.of("text", t(chatId, "btn_back"), "callback_data", "close_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "btn_main"), "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskCustomQuantity error: " + e.getMessage());
    }
}
public void editPharmacyMessageAskName(Long chatId,
                                       Integer messageId,
                                       String name,
                                       String area,
                                       String phone,
                                       Double distance,
                                       Double latitude,
                                       Double longitude,
                                       Long pharmacyId,
                                       Double rating,
                                       Integer stockQuantity,
                                       boolean outOfStock,
                                       String medicineName,
                                       BigDecimal price,
                                       boolean openNow,
                                       String openTime,
                                       String closeTime,
                                       Integer quantity) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "📦 <b>Reserve Medicine</b>\n" +
                "💊 Medicine: " + medicineName + "\n" +
                "🔢 Quantity: " + quantity + "\n\n" +
                "👤 <b>Please enter your full name</b>\n" +
                "Example:\nTeketsel Beyene"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName)
        ));
        keyboard.add(List.of(
                Map.of("text", t(chatId, "btn_back"), "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "btn_main"), "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskName error: " + e.getMessage());
    }
}
public void editPharmacyMessageAskPhone(Long chatId,
                                        Integer messageId,
                                        String name,
                                        String area,
                                        String phone,
                                        Double distance,
                                        Double latitude,
                                        Double longitude,
                                        Long pharmacyId,
                                        Double rating,
                                        Integer stockQuantity,
                                        boolean outOfStock,
                                        String medicineName,
                                        BigDecimal price,
                                        boolean openNow,
                                        String openTime,
                                        String closeTime,
                                        Integer quantity,
                                        String customerName) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
            "📍 " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "📦 <b>Reserve Medicine</b>\n" +
                "💊 Medicine: " + medicineName + "\n" +
                "🔢 Quantity: " + quantity + "\n" +
                "👤 Name: " + customerName + "\n\n" +
                "📱 <b>Please type your phone number</b>\n" +
                "Example:\n0912345678"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName)
        ));
        keyboard.add(List.of(
                Map.of("text", t(chatId, "btn_back"), "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "btn_main"), "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskPhone error: " + e.getMessage());
    }
}
public void editPharmacyMessageToDetails(Long chatId,
                                         Integer messageId,
                                         String name,
                                         String fullAddress,
                                         String formattedAddress,
                                            String landmark,
                                            String plusCode,
                                         String phone,
                                         Double distance,
                                         Double latitude,
                                         Double longitude,
                                         Long pharmacyId,
                                         Double rating,
                                         boolean canRate,
                                         Integer stockQuantity,
                                         boolean outOfStock,
                                         String medicineName,
                                                      Long medicineId,
                                         BigDecimal price,
                                         boolean openNow,
                                         String openTime,
                                         String closeTime,
                                         String lastStockUpdate,
                                         boolean isFavorite) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
            ? t(chatId, "card_price_not_set")
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " " + t(chatId, "card_km");

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursValue;
        if (openTime != null && closeTime != null) {
            hoursValue = openTime + " - " + closeTime;
        } else {
            hoursValue = t(chatId, "card_hours_not_set");
        }

        String statusText = openNow ? t(chatId, "card_status_open") : t(chatId, "card_status_closed");
        String stockText = outOfStock
                ? t(chatId, "card_stock_out")
                : t(chatId, "card_stock_left", stockQuantity == null ? 0 : stockQuantity);

        String stockUpdatedText = (lastStockUpdate == null || lastStockUpdate.isBlank())
                ? "N/A"
                : lastStockUpdate;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "ℹ️ <b>" + t(chatId, "card_pharmacy_details_title") + "</b>\n\n" +
                "🏥 <b>" + t(chatId, "card_name_label") + "</b> " + name + "\n" +
                "💊 <b>" + t(chatId, "card_medicine_label") + "</b> " + displayMedicine(chatId, medicineName) + "\n" +
                "📍 <b>" + t(chatId, "card_address_label") + "</b> " + displayLocationAddress(chatId, fullAddress) + "\n" +
                 (formattedAddress != null && !formattedAddress.isBlank()
            ? "📍 <b>" + t(chatId, "card_exact_address_label") + "</b> " + displayLocationAddress(chatId, formattedAddress) + "\n"
        : "")
+ (landmark != null && !landmark.isBlank()
            ? "🏢 <b>" + t(chatId, "card_landmark_label") + "</b> " + landmark + "\n"
        : "")
+ (plusCode != null && !plusCode.isBlank()
            ? "➕ <b>" + t(chatId, "card_plus_code_label") + "</b> " + plusCode + "\n"
        : "")+
                " <b>" + t(chatId, "card_distance_label") + "</b> " + distanceText + "\n" +
                "⭐ <b>" + t(chatId, "card_rating_label") + "</b> " + ratingText + "/5\n" +
                "💰 <b>" + t(chatId, "card_price_label") + "</b> " + priceText + "\n" +
                "🕒 <b>" + t(chatId, "card_hours_label") + "</b> " + hoursValue + "\n" +
                "📌 <b>" + t(chatId, "card_status_label") + "</b> " + statusText + "\n" +
                "📦 <b>" + t(chatId, "card_stock_label") + "</b> " + stockText + "\n" +
                "🕘 <b>" + t(chatId, "card_last_stock_update_label") + "</b> " + stockUpdatedText
        );

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        row2.add(Map.of("text", t(chatId, "card_hide_details_btn"), "callback_data", "hide_details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        inlineKeyboard.add(List.of(
                isFavorite
                        ? Map.of("text", t(chatId, "card_saved_btn"), "callback_data", "fav_remove_" + pharmacyId)
                        : Map.of("text", t(chatId, "card_save_btn"), "callback_data", "fav_add_" + pharmacyId)
        ));

        List<Map<String, Object>> row4 = new ArrayList<>();
        row4.add(miniAppPhotosButton(chatId, pharmacyId));
        if (medicineId != null) {
            row4.add(miniAppMedicinePhotosButton(chatId, medicineId));
        }
        inlineKeyboard.add(row4);

        inlineKeyboard.add(List.of(
            Map.of("text", t(chatId, "card_report_btn"), "callback_data", "report_issue_" + pharmacyId + "_" + medicineName)
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToDetails error: " + e.getMessage());
    }
}
public void sendLandmarkChoiceKeyboard(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", t(chatId, "reg_landmark_keyboard_text"));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", t(chatId, "btn_skip_landmark"))),
                List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                List.of(Map.of("text", t(chatId, "btn_cancel")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendLandmarkChoiceKeyboard error: " + e.getMessage());
    }
}

public void sendRegistrationPlusCodePrompt(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", t(chatId, "reg_plus_code_prompt"));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", t(chatId, "btn_skip_plus_code"))),
                List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                List.of(Map.of("text", t(chatId, "btn_cancel")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendRegistrationPlusCodePrompt error: " + e.getMessage());
    }
}

public void sendRegistrationLicenseExpiryPrompt(Long chatId) {
    sendExpiryDatePicker(chatId, t(chatId, "expiry_picker_reg_title"));
}

public void sendLicenseUpdateExpiryPrompt(Long chatId) {
    sendExpiryDatePicker(chatId, t(chatId, "expiry_picker_update_title"));
}

private void sendExpiryDatePicker(Long chatId, String titleLine) {
    try {
        int baseYear = java.time.LocalDate.now().getYear();
        String text = titleLine + "\n\n" + t(chatId, "expiry_picker_hint");

        String url = apiUrl + "/sendMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", Map.of("inline_keyboard", buildExpiryYearKeyboard(baseYear)));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendExpiryDatePicker error: " + e.getMessage());
    }
}

public void editExpiryYearPicker(Long chatId, Integer messageId, int baseYear) {
    int safe = Math.max(baseYear, java.time.LocalDate.now().getYear());
    editMessageTextWithInlineKeyboard(
            chatId, messageId,
            "📅 <b>Select Year</b> — " + safe + " – " + (safe + 5),
            buildExpiryYearKeyboard(safe)
    );
}

public void editExpiryMonthPicker(Long chatId, Integer messageId, int year) {
    editMessageTextWithInlineKeyboard(
            chatId, messageId,
            "📅 <b>Select Month</b> — " + year,
            buildExpiryMonthKeyboard(year)
    );
}

public void editExpiryDayPicker(Long chatId, Integer messageId, int year, int month) {
    String mName = java.time.Month.of(month)
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    editMessageTextWithInlineKeyboard(
            chatId, messageId,
            "📅 <b>Select Day</b> — " + mName + " " + year,
            buildExpiryDayKeyboard(year, month)
    );
}

public void editExpiryConfirmation(Long chatId, Integer messageId, java.time.LocalDate date) {
    String label = date.format(
            java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.ENGLISH));
    try {
        String url = apiUrl + "/editMessageText";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", "✅ <b>Expiry Date Selected</b>\n\n📅 " + label);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", Map.of("inline_keyboard", List.of()));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editExpiryConfirmation error: " + e.getMessage());
    }
}

// ---- expiry picker keyboard builders ----

private List<List<Map<String, Object>>> buildExpiryYearKeyboard(int baseYear) {
    int thisYear = java.time.LocalDate.now().getYear();
    int year = Math.max(baseYear, thisYear);
    int thisMonth = java.time.LocalDate.now().getMonthValue();
    String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    List<List<Map<String, Object>>> kb = new ArrayList<>();

    String prevCb = (year - 1 >= thisYear) ? "expiry:y:" + (year - 1) : "expiry:noop";
    kb.add(List.of(
            Map.of("text", "◀", "callback_data", prevCb),
            Map.of("text", String.valueOf(year), "callback_data", "expiry:noop"),
            Map.of("text", "▶", "callback_data", "expiry:y:" + (year + 1))
    ));

    // month grid: 3 columns × 4 rows
    for (int row = 0; row < 4; row++) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (int col = 0; col < 3; col++) {
            int m = row * 3 + col + 1;
            boolean past = (year == thisYear && m < thisMonth);
            r.add(Map.of(
                    "text", past ? "·" : names[m - 1],
                    "callback_data", past ? "expiry:noop" : "expiry:m:" + year + ":" + m
            ));
        }
        kb.add(r);
    }

    int gridBase = Math.max(thisYear, year - ((year - thisYear) % 6));
    kb.add(List.of(Map.of("text", "⬅️ Year", "callback_data", "expiry:yn:" + gridBase)));
    return kb;
}

private List<List<Map<String, Object>>> buildExpiryMonthKeyboard(int year) {
    int thisYear = java.time.LocalDate.now().getYear();
    int thisMonth = java.time.LocalDate.now().getMonthValue();
    String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    List<List<Map<String, Object>>> kb = new ArrayList<>();
    for (int row = 0; row < 4; row++) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (int col = 0; col < 3; col++) {
            int month = row * 3 + col + 1;
            boolean past = year == thisYear && month < thisMonth;
            r.add(Map.of(
                    "text", past ? "·" : names[month - 1],
                    "callback_data", past ? "expiry:noop" : "expiry:m:" + year + ":" + month
            ));
        }
        kb.add(r);
    }

    int gridBase = Math.max(thisYear, year - ((year - thisYear) % 6));
    kb.add(List.of(Map.of("text", "⬅️ Year", "callback_data", "expiry:yn:" + gridBase)));
    return kb;
}

private List<List<Map<String, Object>>> buildExpiryDayKeyboard(int year, int month) {
    java.time.YearMonth ym = java.time.YearMonth.of(year, month);
    int dim = ym.lengthOfMonth();
    java.time.LocalDate today = java.time.LocalDate.now();
    String[] mNames = {"Jan","Feb","Mar","Apr","May","Jun",
                       "Jul","Aug","Sep","Oct","Nov","Dec"};

    int prevY = (month == 1) ? year - 1 : year;
    int prevM = (month == 1) ? 12 : month - 1;
    int nextY = (month == 12) ? year + 1 : year;
    int nextM = (month == 12) ? 1 : month + 1;
    boolean prevOk = !java.time.YearMonth.of(prevY, prevM).isBefore(java.time.YearMonth.from(today));

    List<List<Map<String, Object>>> kb = new ArrayList<>();

    // month nav row
    kb.add(List.of(
            Map.of("text", "◀", "callback_data", prevOk ? "expiry:dm:" + prevY + ":" + prevM : "expiry:noop"),
            Map.of("text", mNames[month - 1] + " " + year, "callback_data", "expiry:noop"),
            Map.of("text", "▶", "callback_data", "expiry:dm:" + nextY + ":" + nextM)
    ));

    // day-of-week header
    kb.add(List.of(
            Map.of("text", "Mo", "callback_data", "expiry:noop"),
            Map.of("text", "Tu", "callback_data", "expiry:noop"),
            Map.of("text", "We", "callback_data", "expiry:noop"),
            Map.of("text", "Th", "callback_data", "expiry:noop"),
            Map.of("text", "Fr", "callback_data", "expiry:noop"),
            Map.of("text", "Sa", "callback_data", "expiry:noop"),
            Map.of("text", "Su", "callback_data", "expiry:noop")
    ));

    // fill days
    int startDow = ym.atDay(1).getDayOfWeek().getValue(); // Mon=1 … Sun=7
    List<Map<String, Object>> row = new ArrayList<>();
    for (int i = 1; i < startDow; i++) row.add(Map.of("text", " ", "callback_data", "expiry:noop"));

    for (int d = 1; d <= dim; d++) {
        java.time.LocalDate dt = java.time.LocalDate.of(year, month, d);
        boolean past = dt.isBefore(today);
        row.add(Map.of(
                "text", past ? "·" : String.valueOf(d),
                "callback_data", past ? "expiry:noop" : "expiry:d:" + year + ":" + month + ":" + d
        ));
        if (row.size() == 7) { kb.add(new ArrayList<>(row)); row.clear(); }
    }
    if (!row.isEmpty()) {
        while (row.size() < 7) row.add(Map.of("text", " ", "callback_data", "expiry:noop"));
        kb.add(new ArrayList<>(row));
    }

    kb.add(List.of(Map.of("text", "⬅️ Month", "callback_data", "expiry:by:" + year + ":" + month)));
    return kb;
}

public void sendRegistrationExactAddressPrompt(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", t(chatId, "reg_exact_address_prompt"));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", t(chatId, "btn_skip_exact_address"))),
                List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                List.of(Map.of("text", t(chatId, "btn_cancel")))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendRegistrationExactAddressPrompt error: " + e.getMessage());
    }
}
public Integer sendPharmacyMapPreview(Long chatId,
                                      Double latitude,
                                      Double longitude,
                                      String pharmacyName,
                                      String address) {
    try {
        if (latitude == null || longitude == null) {
            return null;
        }

        String url = apiUrl + "/sendVenue";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("title", pharmacyName == null || pharmacyName.isBlank() ? "Pharmacy Location" : pharmacyName);
        body.put("address", address == null || address.isBlank() ? "Pharmacy Address" : address);

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> result) {
                Object messageIdObj = result.get("message_id");
                if (messageIdObj instanceof Number number) {
                    return number.intValue();
                }
            }
        }
    } catch (Exception e) {
        System.out.println("sendPharmacyMapPreview error: " + e.getMessage());
    }

    return null;
}
public void restoreNormalPharmacyButtonsAfterRating(Long chatId,
                                                    Integer messageId,
                                                    Long pharmacyId,
                                                    String medicineName,
                                                    Long medicineId,
                                                    Double latitude,
                                                    Double longitude,
                                                    String phone,
                                                    boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        inlineKeyboard.add(row2);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreNormalPharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void restoreRateButtonAfterCancel(Long chatId,
                                         Integer messageId,
                                         Long pharmacyId,
                                         String medicineName,
                                         Long medicineId,
                                         Double latitude,
                                         Double longitude,
                                         String phone,
                                         boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        inlineKeyboard.add(row2);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreRateButtonAfterCancel error: " + e.getMessage());
    }
}
public void togglePharmacyReservePicker(Long chatId,
                                        Integer messageId,
                                        String name,
                                        String area,
                                        String phone,
                                        Double distance,
                                        Double latitude,
                                        Double longitude,
                                        Long pharmacyId,
                                        Double rating,
                                        boolean canRate,
                                        Integer stockQuantity,
                                        boolean outOfStock,
                                        String medicineName,
                                        BigDecimal price,
                                        boolean openNow,
                                        String openTime,
                                        String closeTime) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_close_reserve_btn"), "callback_data", "close_reserve_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "1", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_1"),
                    Map.of("text", "2", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_2"),
                    Map.of("text", "3", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_3")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "5", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_5"),
                    Map.of("text", "10", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_10")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "btn_other"), "callback_data", "res_qty_custom_" + pharmacyId + "_" + medicineName)
            ));
        }

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_rate_btn"), "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        inlineKeyboard.add(List.of(
            Map.of("text", t(chatId, "card_report_btn"), "callback_data", "report_issue_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("togglePharmacyReservePicker error: " + e.getMessage());
    }
}
public void restorePharmacyCardButtons(Long chatId,
                                       Integer messageId,
                                       Long pharmacyId,
                                       String medicineName,
                                       Double latitude,
                                       Double longitude,
                                       String phone,
                                       Long medicineId,
                                       boolean outOfStock,
                                       boolean openNow,
                                       boolean canRate) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";
        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        if (!outOfStock && openNow && medicineId != null) {
            row1.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }
        if (!row1.isEmpty()) {
            inlineKeyboard.add(row1);
        }

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        inlineKeyboard.add(row2);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyCardButtons error: " + e.getMessage());
    }
}
public void editPharmacyMessageToRatingPicker(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              String medicineName,
                                              Long medicineId,
                                              Double latitude,
                                              Double longitude,
                                              String phone,
                                              boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", t(chatId, "card_call_btn"), "callback_data", "call_" + pharmacyId + "_" + medicineName));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
          if (!outOfStock && medicineId != null) {
              row2.add(directReserveButton(chatId, pharmacyId, medicineId));
        }
        row2.add(Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        inlineKeyboard.add(List.of(
                Map.of("text", "⭐ 1", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_1"),
                Map.of("text", "⭐ 2", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_2"),
                Map.of("text", "⭐ 3", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_3"),
                Map.of("text", "⭐ 4", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_4"),
                Map.of("text", "⭐ 5", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_5")
        ));

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "btn_cancel"), "callback_data", "cancel_rate_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToRatingPicker error: " + e.getMessage());
    }
}
public void restorePharmacyButtonsAfterRating(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              Double latitude,
                                              Double longitude) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                        Map.of("text", t(chatId, "btn_rated"), "callback_data", "rated_done")
                )
        );

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void restoreNormalPharmacyButtons(Long chatId,
                                         Integer messageId,
                                         Long pharmacyId,
                                         String medicineName,
                                         Double latitude,
                                         Double longitude,
                                         boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                    Map.of("text", t(chatId, "card_reserve_btn"), "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName)
            ));
        } else {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl)
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "card_rate_btn"), "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreNormalPharmacyButtons error: " + e.getMessage());
    }
}
public void restorePharmacyButtonsAfterRating(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              String medicineName,
                                              Double latitude,
                                              Double longitude,
                                              boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl),
                    Map.of("text", t(chatId, "card_reserve_btn"), "callback_data", "reserve_" + pharmacyId + "_" + medicineName)
            ));
        } else {
            inlineKeyboard.add(List.of(
                    Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl)
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "card_details_btn"), "callback_data", "details_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "btn_rated"), "callback_data", "rated_done")
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void sendRatingPicker(Long chatId, Long pharmacyId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⭐ <b>Rate this pharmacy</b>\n\nChoose a rating:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "⭐ 1", "callback_data", "rate_" + pharmacyId + "_1"),
                        Map.of("text", "⭐ 2", "callback_data", "rate_" + pharmacyId + "_2"),
                        Map.of("text", "⭐ 3", "callback_data", "rate_" + pharmacyId + "_3"),
                        Map.of("text", "⭐ 4", "callback_data", "rate_" + pharmacyId + "_4"),
                        Map.of("text", "⭐ 5", "callback_data", "rate_" + pharmacyId + "_5")
                ),
                List.of(
                        Map.of("text", "❌ Cancel", "callback_data", "cancel_rate")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendRatingPicker error: " + e.getMessage());
    }
}
public void sendPharmacyDetails(Long chatId,
                                String name,
                                String area,
                                String phone,
                                Double distance,
                                Double rating,
                                BigDecimal price,
                                Integer stockQuantity,
                                boolean outOfStock,
                                boolean openNow,
                                String openTime,
                                String closeTime,
                                String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
                ? "not set"
                : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " km";
        String stockText = outOfStock
                ? "Out of stock"
                : ((stockQuantity == null ? 0 : stockQuantity) + " left");
        String hoursText = (openTime != null && closeTime != null)
                ? openTime + " - " + closeTime
                : "Not set";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "ℹ️ <b>Pharmacy Details</b>\n\n" +
                "🏥 <b>Name:</b> " + name + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
            "📍 <b>Area:</b> " + (area == null ? "N/A" : displayLocation(chatId, area)) + "\n" +
                "📞 <b>Phone:</b> " + (phone == null ? "N/A" : phone) + "\n" +
                "📏 <b>Distance:</b> " + distanceText + "\n" +
                "⭐ <b>Rating:</b> " + ratingText + "/5\n" +
                "💰 <b>Price:</b> " + priceText + "\n" +
                "🕒 <b>Hours:</b> " + hoursText + "\n" +
                "📌 <b>Status:</b> " + (openNow ? "Open now" : "Closed") + "\n" +
                "📦 <b>Stock:</b> " + stockText
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyDetails error: " + e.getMessage());
    }
}

public void sendIssueTypePicker(Long chatId, Long pharmacyId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("parse_mode", "HTML");
        body.put("text", "⚠️ <b>" + t(chatId, "issue_report_choose_type") + "</b>");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_price"), "callback_data", "issue_type_price_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "issue_type_stock"), "callback_data", "issue_type_stock_" + pharmacyId + "_" + medicineName)
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_location"), "callback_data", "issue_type_location_" + pharmacyId + "_" + medicineName),
                Map.of("text", t(chatId, "issue_type_service"), "callback_data", "issue_type_service_" + pharmacyId + "_" + medicineName)
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_other"), "callback_data", "issue_type_other_" + pharmacyId + "_" + medicineName)
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "issue_type_cancel"), "callback_data", "issue_type_cancel_" + pharmacyId + "_" + medicineName)
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendIssueTypePicker error: " + e.getMessage());
    }
}

    public void lockRatingKeepNavigation(Long chatId, Integer messageId, double lat, double lon) {
        try {
            String mapLink = "https://www.google.com/maps?q=" + lat + "," + lon;

            Map<String, Object> mapBtn = Map.of("text", t(chatId, "card_navigate_btn"), "url", mapLink);
            List<List<Map<String, Object>>> keyboard = List.of(List.of(mapBtn));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(apiUrl + "/editMessageReplyMarkup", body, String.class);
        } catch (Exception e) {
            System.out.println("lockRatingKeepNavigation error: " + e.getMessage());
        }
    }

    /* ---------------- PROFILE / INVENTORY ---------------- */

 public void sendUpdateMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⚙️ Pharmacy Profile Update\n\nSelect what you want to update:");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📞 Update Phone")),
                List.of(Map.of("text", "📄 Update License")),
                List.of(Map.of("text", "⏰ Update Hours")),
                List.of(Map.of("text", "💊 Update Medicines")),
                List.of(Map.of("text", "📍 Update Location")),
                List.of(Map.of("text", "🖼 Manage Photos")),
            List.of(Map.of("text", "💊 Manage Medicine Photos")),
            List.of(Map.of("text", "🛑 Temporary Close")),
            List.of(Map.of("text", "✅ Reopen Now")),
                List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendUpdateMenu error: " + e.getMessage());
    }
}

public void sendPharmacyPhotoManagementMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "🖼 <b>Manage Photos</b>\n\nChoose an action:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "➕ Add Photo")),
                List.of(Map.of("text", "👁 View Photos")),
                List.of(Map.of("text", "⭐ Set Main Photo")),
                List.of(Map.of("text", "🗑 Remove Photo")),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendPharmacyPhotoManagementMenu error: " + e.getMessage());
    }
}

public void sendMedicinePhotoManagementMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "💊🖼 <b>Manage Medicine Photos</b>\n\nChoose an action:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "➕ Add Medicine Photo")),
                List.of(Map.of("text", "👁 View Medicine Photos")),
                List.of(Map.of("text", "⭐ Set Main Medicine Photo")),
                List.of(Map.of("text", "🗑 Remove Medicine Photo")),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendMedicinePhotoManagementMenu error: " + e.getMessage());
    }
}

public void sendPharmacyMedicineIndexedList(Long chatId, String title, List<com.tenahub.bot.entity.PharmacyInventory> medicines) {
    try {
        StringBuilder text = new StringBuilder();
        text.append("💊 <b>").append(escapeHtml(title)).append("</b>\n\n");

        if (medicines == null || medicines.isEmpty()) {
            text.append("No medicines found.");
            sendMessage(chatId, text.toString(), "HTML");
            return;
        }

        for (int i = 0; i < medicines.size(); i++) {
            var item = medicines.get(i);
            String prescriptionBadge = item.isRequiresPrescription() ? " [Rx]" : " [OTC]";
            text.append(i + 1)
                    .append(". #")
                    .append(item.getId())
                    .append(" ")
                    .append(escapeHtml(displayMedicine(chatId, safe(item.getMedicineName()))))
                .append(prescriptionBadge)
                    .append("\n");
        }

        sendMessage(chatId, text.toString().trim(), "HTML");
    } catch (Exception e) {
        System.out.println("sendPharmacyMedicineIndexedList error: " + e.getMessage());
    }
}

public void sendMedicinePhotoIndexedList(Long chatId, String title, List<com.tenahub.bot.entity.MedicinePhoto> photos) {
    try {
        StringBuilder text = new StringBuilder();
        text.append("💊🖼 <b>").append(escapeHtml(title)).append("</b>\n\n");

        if (photos == null || photos.isEmpty()) {
            text.append("No photos found.");
            sendMessage(chatId, text.toString(), "HTML");
            return;
        }

        int limit = Math.min(photos.size(), com.tenahub.bot.service.MedicinePhotoService.MAX_PHOTOS);
        for (int i = 0; i < limit; i++) {
            var p = photos.get(i);
            text.append(i + 1)
                    .append(". #")
                    .append(p.getId())
                    .append(p.isMainPhoto() ? " ⭐ main" : "")
                    .append("\n");
        }

        sendMessage(chatId, text.toString().trim(), "HTML");
    } catch (Exception e) {
        System.out.println("sendMedicinePhotoIndexedList error: " + e.getMessage());
    }
}

public void sendMedicinePhotosForPharmacy(Long chatId, String medicineName, List<String> photoFileIds) {
    try {
        if (photoFileIds == null || photoFileIds.isEmpty()) {
            sendMessage(chatId, "No medicine photos available yet.");
            return;
        }

        String caption = "💊 <b>" + escapeHtml(displayMedicine(chatId, safe(medicineName))) + "</b>\n"
                + "🖼 Medicine photos";

        if (photoFileIds.size() == 1) {
            sendPhoto(chatId, photoFileIds.get(0), caption);
            return;
        }

        sendPhotoMediaGroup(chatId, photoFileIds, caption);
    } catch (Exception e) {
        System.out.println("sendMedicinePhotosForPharmacy error: " + e.getMessage());
    }
}

public void sendPharmacyPhotoIndexedList(Long chatId, String title, List<com.tenahub.bot.entity.PharmacyPhoto> photos) {
    try {
        StringBuilder text = new StringBuilder();
        text.append("🖼 <b>").append(escapeHtml(title)).append("</b>\n\n");

        if (photos == null || photos.isEmpty()) {
            text.append("No photos found.");
            sendMessage(chatId, text.toString(), "HTML");
            return;
        }

        int limit = Math.min(photos.size(), 4);
        for (int i = 0; i < limit; i++) {
            var p = photos.get(i);
            text.append(i + 1)
                    .append(". #")
                    .append(p.getId())
                    .append(p.isMainPhoto() ? " ⭐ main" : "")
                    .append("\n");
        }

        sendMessage(chatId, text.toString().trim(), "HTML");
    } catch (Exception e) {
        System.out.println("sendPharmacyPhotoIndexedList error: " + e.getMessage());
    }
}

public void sendPharmacyPhotosForUser(Long chatId,
                                      String pharmacyName,
                                      String area,
                                      String landmark,
                                      List<String> photoFileIds) {
    try {
        if (photoFileIds == null || photoFileIds.isEmpty()) {
            sendMessage(chatId, "No pharmacy photos available yet.");
            return;
        }

        String caption = "🏥 <b>" + escapeHtml(safe(pharmacyName)) + "</b>\n"
                + "📍 " + escapeHtml(safe(area));
        if (landmark != null && !landmark.isBlank()) {
            caption += "\n🧭 " + escapeHtml(landmark);
        }

        if (photoFileIds.size() == 1) {
            sendPhoto(chatId, photoFileIds.get(0), caption);
            return;
        }

        sendPhotoMediaGroup(chatId, photoFileIds, caption);
    } catch (Exception e) {
        System.out.println("sendPharmacyPhotosForUser error: " + e.getMessage());
    }
}

public void sendPhotoMediaGroup(Long chatId, List<String> fileIds, String firstCaption) {
    try {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        String url = apiUrl + "/sendMediaGroup";
        List<Map<String, Object>> media = new ArrayList<>();

        for (int i = 0; i < fileIds.size(); i++) {
            String fileId = fileIds.get(i);
            if (fileId == null || fileId.isBlank()) {
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("type", "photo");
            item.put("media", fileId);
            if (i == 0 && firstCaption != null && !firstCaption.isBlank()) {
                item.put("caption", firstCaption);
                item.put("parse_mode", "HTML");
            }
            media.add(item);
        }

        if (media.isEmpty()) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("media", media);

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendPhotoMediaGroup error: " + e.getMessage());
    }
}

public void sendTemporaryCloseDurationPicker(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("parse_mode", "HTML");
        body.put("text", "🛑 <b>Temporary Close</b>\n\nChoose how long your pharmacy should be closed:");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
        inlineKeyboard.add(List.of(
                Map.of("text", "2h", "callback_data", "temp_close_duration_2"),
                Map.of("text", "6h", "callback_data", "temp_close_duration_6"),
                Map.of("text", "12h", "callback_data", "temp_close_duration_12")
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", "24h", "callback_data", "temp_close_duration_24"),
                Map.of("text", "48h", "callback_data", "temp_close_duration_48"),
                Map.of("text", "72h", "callback_data", "temp_close_duration_72")
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", "✅ Reopen Now", "callback_data", "temp_close_reopen_now")
        ));
        inlineKeyboard.add(List.of(
            Map.of("text", "❌ Cancel", "callback_data", "temp_close_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendTemporaryCloseDurationPicker error: " + e.getMessage());
    }
}

public void sendTemporaryCloseReasonPicker(Long chatId, int durationHours) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("parse_mode", "HTML");
        body.put("text", "🛑 <b>Temporary Close</b>\n\nDuration: <b>" + durationHours + " hour(s)</b>\nChoose a reason:");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
        inlineKeyboard.add(List.of(
                Map.of("text", "⚡ Power outage", "callback_data", "temp_close_reason_power"),
                Map.of("text", "🛠️ Maintenance", "callback_data", "temp_close_reason_maintenance")
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", "🚚 Stock refill", "callback_data", "temp_close_reason_refill"),
                Map.of("text", "👥 Staff shortage", "callback_data", "temp_close_reason_staff")
        ));
        inlineKeyboard.add(List.of(
                Map.of("text", "🎉 Holiday/Break", "callback_data", "temp_close_reason_holiday"),
                Map.of("text", "✍️ Other", "callback_data", "temp_close_reason_other")
        ));
        inlineKeyboard.add(List.of(
            Map.of("text", "❌ Cancel", "callback_data", "temp_close_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendTemporaryCloseReasonPicker error: " + e.getMessage());
    }
}

    public void sendInventoryMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "📦 <b>Inventory Management</b>\n\nChoose an action:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "➕ Add / Update Stock"),
                    Map.of("text", "📦 Bulk Inventory Update")
                ),
                List.of(
                    Map.of("text", "💰 Update Price")
                ),
                List.of(
                    Map.of("text", "🧾 Prescription Settings")
                ),
                List.of(
                        Map.of("text", "📉 Mark Out of Stock"),
                        Map.of("text", "📋 View Inventory")
                ),
                List.of(
                        Map.of("text", "📤 Export Inventory"),
                        Map.of("text", "📥 Import Inventory CSV")
                ),
                List.of(
                        Map.of("text", "📊 Inventory Summary"),
                        Map.of("text", "⚠️ Low Stock Alert")
                ),
                List.of(
                        Map.of("text", "📈 Demand Insights"),
                        Map.of("text", "🎯 Set Low Stock Threshold")
                ),
                List.of(
                    Map.of("text", "💡 Restock Suggestions")
                ),
                List.of(
                        Map.of("text", "🔙 Back"),
                        Map.of("text", "🏠 Home")
                ),
                List.of(
                        Map.of("text", "❌ Cancel")
                )
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendInventoryMenu error: " + e.getMessage());
    }
}

    public void sendBulkInventoryUpdateInstructions(Long chatId) {
        String text = "📦 <b>Bulk Inventory Update</b>\n\n"
                + "Send one medicine per line using this format:\n"
                + "medicine | quantity | price\n\n"
                + "Example:\n"
                + "insulin | 12 | 300\n"
                + "ibuprofen | 20 | 80\n\n"
                + "Optional:\n"
                + "medicine | quantity | price | threshold\n\n"
                + "Rules:\n"
                + "• quantity must be integer\n"
                + "• price must be decimal\n"
                + "• threshold is optional\n"
                + "• blank lines are ignored";
        sendMessage(chatId, text);
    }

    public void sendBulkInventoryUpdateResult(Long chatId, com.tenahub.bot.service.InventoryService.BulkInventoryUpdateResult result) {
        if (result == null) {
            sendMessage(chatId, "⚠️ Bulk update failed: no result.");
            return;
        }

        String title = result.failedCount() == 0
                ? "✅ <b>Bulk Inventory Update Complete</b>"
                : "⚠️ <b>Bulk Inventory Update Complete</b>";

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n")
                .append("Total lines: ").append(result.totalLines()).append("\n")
                .append("Updated: ").append(result.updatedCount()).append("\n")
                .append("Failed: ").append(result.failedCount());

        if (result.errors() != null && !result.errors().isEmpty()) {
            sb.append("\n\n<b>Errors:</b>\n");
            int limit = Math.min(20, result.errors().size());
            for (int i = 0; i < limit; i++) {
                sb.append("• ").append(result.errors().get(i)).append("\n");
            }
            if (result.errors().size() > limit) {
                sb.append("• ... and ").append(result.errors().size() - limit).append(" more");
            }
        }

        sendMessage(chatId, sb.toString().trim());
    }

    public void sendSummaryMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📊 <b>Inventory Summary</b>\n\nChoose a period:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📊 Daily Summary")),
                    List.of(Map.of("text", "📊 Weekly Summary")),
                    List.of(Map.of("text", "📊 Monthly Summary")),
                    List.of(Map.of("text", "📊 Yearly Summary")),
                    List.of(Map.of("text", "⚠️ Low Stock Alert")),
                    List.of(Map.of("text", "📈 Demand Insights")),
                    List.of(Map.of("text", "💡 Restock Suggestions")),
                    List.of(Map.of("text", "🎯 Set Low Stock Threshold")),
                    List.of(Map.of("text", "📥 Import Inventory CSV")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSummaryMenu error: " + e.getMessage());
        }
    }
    public void sendPharmacyPhoto(Long chatId, String photoFileId, String caption) {
    try {
        if (photoFileId == null || photoFileId.isBlank()) {
            return;
        }

        String url = apiUrl + "/sendPhoto";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", photoFileId);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyPhoto error: " + e.getMessage());
    }
}
public void deleteMessage(Long chatId, Integer messageId) {
    try {
        if (chatId == null || messageId == null) {
            return;
        }

        String url = apiUrl + "/deleteMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("deleteMessage error: " + e.getMessage());
    }
}
    public void sendLowStockAlert(Long chatId, String medicineName, Integer quantity, Integer threshold) {
        try {
            String text = "⚠️ <b>Low Stock Alert</b>\n\n"
                    + "💊 Medicine: " + displayMedicine(chatId, medicineName) + "\n"
                    + "📦 Current quantity: " + (quantity == null ? 0 : quantity) + "\n"
                    + "🎯 Threshold: " + (threshold == null ? 0 : threshold) + "\n\n"
                    + "Please restock soon.";

            sendMessage(chatId, text);
        } catch (Exception e) {
            System.out.println("sendLowStockAlert error: " + e.getMessage());
        }
    }

    /* ---------------- MULTI MEDICINE ---------------- */

    public void sendMultiMedicineStartMenu(Long chatId, boolean hasSavedLocation) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "multi_search_intro"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            if (hasSavedLocation) {
                keyboard.add(List.of(Map.of("text", t(chatId, "btn_use_saved_location"), "callback_data", "multi_loc_saved")));
            }

            keyboard.add(List.of(Map.of("text", t(chatId, "btn_share_current_location"), "callback_data", "multi_loc_share")));
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_main"), "callback_data", "multi_cancel")));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineStartMenu error: " + e.getMessage());
        }
    }

    public void sendMultiMedicinePanel(Long chatId, List<String> selected) {
        try {
            String url = apiUrl + "/sendMessage";

            String heading = (selected != null && selected.size() == 1)
                    ? "🧺 <b>Selected Medicine</b>\n\n"
                    : "🧺 <b>Selected Medicines</b>\n\n";
            StringBuilder text = new StringBuilder(heading);

            if (selected == null || selected.isEmpty()) {
                text.append("No medicines selected yet.");
            } else {
                for (int i = 0; i < selected.size(); i++) {
                    text.append(i + 1).append(". ").append(displayMedicine(chatId, selected.get(i))).append("\n");
                }
            }

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(Map.of("text", t(chatId, "btn_search_pharmacies"))));
            keyboard.add(List.of(
                    Map.of("text", t(chatId, "btn_add_more")),
                    Map.of("text", t(chatId, "btn_clear"))
            ));

            if (selected != null && !selected.isEmpty()) {
                for (String med : selected) {
                    keyboard.add(List.of(
                            Map.of("text", "❌ " + displayMedicine(chatId, med))
                    ));
                }
            }

            keyboard.add(List.of(
                    Map.of("text", t(chatId, "btn_change_location")),
                    Map.of("text", t(chatId, "btn_main"))
            ));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text.toString());
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicinePanel error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineModeKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🧺 Multi-medicine mode is active.");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "btn_search_pharmacies"))),
                    List.of(Map.of("text", t(chatId, "btn_add_more")), Map.of("text", t(chatId, "btn_clear"))),
                    List.of(Map.of("text", t(chatId, "btn_change_location")), Map.of("text", t(chatId, "btn_main")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineModeKeyboard error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineLocationKeyboard(Long chatId, boolean hasSavedLocation) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "multi_search_title"));

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            if (hasSavedLocation) {
                keyboard.add(List.of(
                        Map.of("text", t(chatId, "btn_use_saved_location")),
                        Map.of("text", t(chatId, "btn_share_current_location"))
                ));
            } else {
                keyboard.add(List.of(
                        Map.of("text", t(chatId, "btn_share_current_location"))
                ));
            }

            keyboard.add(List.of(
                    Map.of("text", t(chatId, "select_ethiopia_region_button")),
                    Map.of("text", t(chatId, "btn_main"))
            ));

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineLocationKeyboard error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineChangeLocationMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "change_location_title"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "share_exact_location_button"), "request_location", true)),
                    List.of(Map.of("text", t(chatId, "select_ethiopia_region_button"))),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineChangeLocationMenu error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineExactLocationRequest(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", t(chatId, "multi_share_exact_location_prompt"));
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "share_exact_location_button"), "request_location", true)),
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineExactLocationRequest error: " + e.getMessage());
        }
    }

    public void sendMultiMedicinePharmacyResult(Long chatId, com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO r) {
        try {
            sendLocation(chatId, r.getLatitude(), r.getLongitude());

            String mapLink = "https://www.google.com/maps?q=" + r.getLatitude() + "," + r.getLongitude();

            String text = "🏥 <b>" + r.getName() + "</b>\n"
                    + "📍 " + displayLocation(chatId, r.getArea()) + "\n"
                    + "📏 " + String.format("%.2f km away", r.getDistance()) + "\n"
                    + "📞 " + r.getPhone() + "\n"
                    + "⭐ Rating: " + String.format("%.1f", r.getRating()) + "/5\n"
                    + "🕒 " + (r.isOpenNow() ? "Open now ✅" : "Closed now") + "\n\n"
                    + "✅ Matched: " + r.getMatchedCount() + "/" + (r.getMatchedMedicines().size() + r.getMissingMedicines().size()) + "\n"
                    + "💊 Available: " + (r.getMatchedMedicines().isEmpty() ? "None" : r.getMatchedMedicines().stream().map(m -> displayMedicine(chatId, m)).collect(Collectors.joining(", "))) + "\n"
                    + "❌ Missing: " + (r.getMissingMedicines().isEmpty() ? "None" : r.getMissingMedicines().stream().map(m -> displayMedicine(chatId, m)).collect(Collectors.joining(", ")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(Map.of("text", t(chatId, "card_navigate_btn"), "url", mapLink)));

            if (r.getMatchedCount() > 0) {
                List<Map<String, Object>> reserveRow = new ArrayList<>();

                if (r.getMatchedMedicineIds().size() == 1) {
                    reserveRow.add(miniAppSingleReserveButton(chatId, r.getPharmacyId(), r.getMatchedMedicineIds().get(0)));
                } else {
                    reserveRow.add(Map.of("text", t(chatId, "card_reserve_one_matched_btn"), "callback_data", "multi_reserve_one_" + r.getPharmacyId()));
                }

                reserveRow.add(miniAppMultiReserveButton(chatId, r.getPharmacyId(), r.getMatchedMedicineIds()));
                keyboard.add(reserveRow);
            }

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicinePharmacyResult error: " + e.getMessage());
        }
    }

    public void sendMatchedMedicineReservePicker(Long chatId, Long pharmacyId, List<PharmacyInventory> matchedInventories) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📦 <b>Reserve Matched Medicine</b>\n\n" +
                    "Select one medicine to reserve:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

                for (PharmacyInventory inventory : matchedInventories) {
                String medicineName = inventory == null ? null : inventory.getMedicineName();
                Long medicineId = inventory == null ? null : inventory.getId();
                String reserveUrl = buildMiniAppSingleReserveUrl(pharmacyId, medicineId);
                Map<String, Object> button = canUseWebAppButton(reserveUrl)
                    ? Map.of("text", "💊 " + displayMedicine(chatId, medicineName), "web_app", Map.of("url", reserveUrl))
                    : Map.of("text", "💊 " + displayMedicine(chatId, medicineName), "url", reserveUrl);

                keyboard.add(List.of(
                    button
                ));
            }

            keyboard.add(List.of(
                    Map.of("text", "❌ Cancel", "callback_data", "multi_pick_reserve_cancel")
            ));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMatchedMedicineReservePicker error: " + e.getMessage());
        }
    }

    public void sendMultiReservationUnavailable(Long chatId, Long pharmacyId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    t(chatId, "multi_reserve_unavailable_title") + "\n\n" +
                    t(chatId, "multi_reserve_unavailable_msg")
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", t(chatId, "card_reserve_one_matched_btn"), "callback_data", "multi_reserve_one_" + pharmacyId)
                    ),
                    List.of(
                            Map.of("text", t(chatId, "btn_back"), "callback_data", "multi_pharmacy_back")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReservationUnavailable error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineReservationKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📦 Reservation options");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", t(chatId, "btn_back")), Map.of("text", t(chatId, "btn_main"))),
                    List.of(Map.of("text", t(chatId, "btn_cancel")))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineReservationKeyboard error: " + e.getMessage());
        }
    }

    public void sendMultiReserveMedicineQuantityPicker(Long chatId, java.util.List<String> matchedMedicines) {
        try {
            String url = apiUrl + "/sendMessage";

            StringBuilder medicineList = new StringBuilder();
            medicineList.append("🧺 <b>Multi-Medicine Reservation</b>\n\n");
            medicineList.append("Enter quantities for each medicine:\n\n");

            for (String medicine : matchedMedicines) {
                medicineList.append("• ").append(displayMedicine(chatId, medicine)).append("\n");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", medicineList.toString());
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            for (String medicine : matchedMedicines) {
                List<Map<String, Object>> row = new ArrayList<>();

                for (int qty : new int[]{1, 2, 3, 5, 10}) {
                    row.add(Map.of(
                            "text", String.valueOf(qty),
                            "callback_data", "multi_res_qty_" + medicine.toLowerCase() + "_" + qty
                    ));
                }

                keyboard.add(row);

                keyboard.add(List.of(Map.of(
                        "text", "✏️ " + displayMedicine(chatId, medicine),
                        "callback_data", "multi_res_edit_qty_" + medicine.toLowerCase()
                )));
            }

            keyboard.add(List.of(Map.of("text", "✅ Continue", "callback_data", "multi_res_continue")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel", "callback_data", "multi_res_cancel")));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReserveMedicineQuantityPicker error: " + e.getMessage());
        }
    }

    public void sendMultiReserveQuantityEdit(Long chatId, String medicineName) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🧺 <b>Enter quantity for:</b> " + displayMedicine(chatId, medicineName) + "\n\n" +
                    "Send a number (1-100) or use buttons below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            for (int qty : new int[]{1, 2, 3, 5, 10, 20, 50}) {
                List<Map<String, Object>> row = new ArrayList<>();
                row.add(Map.of("text", String.valueOf(qty), "callback_data", "multi_res_qty_confirm_" + medicineName.toLowerCase() + "_" + qty));
                keyboard.add(row);
            }

            keyboard.add(List.of(Map.of("text", "❌ Skip this medicine", "callback_data", "multi_res_qty_skip_" + medicineName.toLowerCase())));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReserveQuantityEdit error: " + e.getMessage());
        }
    }

    public void sendMultiReserveSummary(Long chatId, java.util.Map<String, Integer> medicineQuantities) {
        try {
            String url = apiUrl + "/sendMessage";

            StringBuilder summary = new StringBuilder();
            summary.append("☑️ <b>Reservation Summary</b>\n\n");

            int totalItems = 0;
            for (java.util.Map.Entry<String, Integer> entry : medicineQuantities.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    summary.append("• ").append(displayMedicine(chatId, entry.getKey()))
                            .append(" × ").append(entry.getValue()).append("\n");
                    totalItems += entry.getValue();
                }
            }

            summary.append("\n📊 Total items: ").append(totalItems).append("\n\n");
            summary.append("Next: Enter your name and phone number");

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", summary.toString());
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "❌ Cancel", "callback_data", "multi_res_cancel"))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReserveSummary error: " + e.getMessage());
        }
    }

    public void sendMultiReserveFinalConfirmation(Long chatId, String customerName, String customerPhone, java.util.Map<String, Integer> medicineQuantities) {
        try {
            String url = apiUrl + "/sendMessage";

            StringBuilder confirmation = new StringBuilder();
            confirmation.append("📋 <b>Final Confirmation</b>\n\n");
            confirmation.append("👤 Name: ").append(customerName).append("\n");
            confirmation.append("📱 Phone: ").append(customerPhone).append("\n\n");
            confirmation.append("<b>Medicines:</b>\n");

            int totalItems = 0;
            for (java.util.Map.Entry<String, Integer> entry : medicineQuantities.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    confirmation.append("• ").append(displayMedicine(chatId, entry.getKey()))
                            .append(" × ").append(entry.getValue()).append("\n");
                    totalItems += entry.getValue();
                }
            }

            confirmation.append("\n📊 Total: ").append(totalItems).append(" items");

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", confirmation.toString());
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "✅ Submit", "callback_data", "multi_res_submit"),
                            Map.of("text", "❌ Cancel", "callback_data", "multi_res_cancel")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReserveFinalConfirmation error: " + e.getMessage());
        }
    }

    public void sendMultiReserveGroupedConfirmation(Long chatId, String groupId, java.util.List<com.tenahub.bot.entity.MedicineReservation> reservations) {
        try {
            String url = apiUrl + "/sendMessage";

            if (reservations == null || reservations.isEmpty()) {
                sendMessage(chatId, "❌ Reservation group is empty.");
                return;
            }

            StringBuilder confirmation = new StringBuilder();
            confirmation.append("✅ <b>Reservations Submitted!</b>\n\n");
            confirmation.append("🧺 <b>Group ID:</b> ").append(groupId.substring(0, 8)).append("...\n\n");

            int totalItems = 0;
            for (com.tenahub.bot.entity.MedicineReservation res : reservations) {
                confirmation.append("• ").append(displayMedicine(chatId, res.getMedicineName()))
                        .append(" × ").append(res.getRequestedQuantity()).append("\n");
                totalItems += res.getRequestedQuantity();
            }

            confirmation.append("\n👤 ").append(reservations.get(0).getCustomerName()).append("\n");
            confirmation.append("📱 ").append(reservations.get(0).getCustomerPhone()).append("\n\n");
            confirmation.append("⏳ Waiting for pharmacy approval...");

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", confirmation.toString());
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiReserveGroupedConfirmation error: " + e.getMessage());
        }
    }

    public void sendPharmacyPendingGroupedReservationCard(Long chatId, String groupId, java.util.List<com.tenahub.bot.entity.MedicineReservation> group) {
        try {
            if (group == null || group.isEmpty()) return;

            String url = apiUrl + "/sendMessage";

            StringBuilder message = new StringBuilder();
            message.append("📦 <b>Pending Group Reservation</b>\n\n");
            message.append("🧺 <b>Group:</b> ").append(groupId.substring(0, 8)).append("...\n\n");

            for (com.tenahub.bot.entity.MedicineReservation r : group) {
                message.append("💊 ").append(displayMedicine(chatId, r.getMedicineName()))
                        .append(" × ").append(r.getRequestedQuantity()).append("\n");
            }

            boolean prescriptionRequired = group.stream().anyMatch(com.tenahub.bot.entity.MedicineReservation::isPrescriptionRequired);
            String prescriptionStatus = group.stream()
                    .filter(com.tenahub.bot.entity.MedicineReservation::isPrescriptionRequired)
                    .map(r -> r.getPrescriptionReviewStatus() == null ? "UPLOAD_REQUIRED" : r.getPrescriptionReviewStatus().name())
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("NOT_REQUIRED");

            com.tenahub.bot.entity.MedicineReservation first = group.get(0);
            message.append("\n👤 <b>Customer:</b> ").append(first.getCustomerName()).append("\n");
            message.append("📱 <b>Phone:</b> ").append(first.getCustomerPhone()).append("\n");
            message.append("👤 <b>User ID:</b> ").append(first.getUserId());
            if (prescriptionRequired) {
                message.append("\n🧾 <b>Prescription:</b> Required (").append(prescriptionStatus).append(")");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

                List<List<Map<String, Object>>> keyboard = prescriptionRequired
                    ? ("PENDING_REVIEW".equalsIgnoreCase(prescriptionStatus)
                        ? List.of(
                            List.of(
                                Map.of("text", "🧾 Review Prescription", "callback_data", "review_pres_group_" + groupId)
                            ),
                            List.of(
                                Map.of("text", "❌ Reject All", "callback_data", "reject_group_" + groupId)
                            )
                        )
                        : List.of(
                            List.of(
                                Map.of("text", "❌ Reject All", "callback_data", "reject_group_" + groupId)
                            )
                        ))
                    : List.of(
                    List.of(
                        Map.of("text", "✅ Approve All", "callback_data", "approve_group_" + groupId),
                        Map.of("text", "❌ Reject All", "callback_data", "reject_group_" + groupId)
                    )
                );
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPharmacyPendingGroupedReservationCard error: " + e.getMessage());
        }
    }

    public void sendPharmacyApprovedGroupedReservationCard(Long chatId, String groupId, java.util.List<com.tenahub.bot.entity.MedicineReservation> group, String holdUntil) {
        try {
            if (group == null || group.isEmpty()) return;

            String url = apiUrl + "/sendMessage";

            String holdLine = (holdUntil != null && !holdUntil.isBlank()) ? "⏳ <b>Hold Until:</b> " + holdUntil + "\n" : "";

            StringBuilder message = new StringBuilder();
            message.append("✅ <b>Approved Group Reservation</b>\n\n");
            message.append("🧺 <b>Group:</b> ").append(groupId.substring(0, 8)).append("...\n\n");

            for (com.tenahub.bot.entity.MedicineReservation r : group) {
                message.append("💊 ").append(displayMedicine(chatId, r.getMedicineName()))
                        .append(" × ").append(r.getRequestedQuantity()).append("\n");
            }

            com.tenahub.bot.entity.MedicineReservation first = group.get(0);
            message.append("\n👤 <b>Customer:</b> ").append(first.getCustomerName()).append("\n");
            message.append("📱 <b>Phone:</b> ").append(first.getCustomerPhone()).append("\n");
            message.append("👤 <b>User ID:</b> ").append(first.getUserId()).append("\n");
            message.append(holdLine);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "📦 Fulfilled All", "callback_data", "fulfill_group_" + groupId),
                            Map.of("text", "❌ Cancel All", "callback_data", "pharmacy_cancel_group_" + groupId)
                    )
            );
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPharmacyApprovedGroupedReservationCard error: " + e.getMessage());
        }
    }

    public void sendPharmacyGroupedReservationCard(Long chatId, String groupId, java.util.List<com.tenahub.bot.entity.MedicineReservation> reservations) {
        try {
            if (reservations == null || reservations.isEmpty()) {
                return;
            }

            boolean awaitingPrescriptionUpload = reservations.stream().anyMatch(reservation ->
                    reservation != null
                            && reservation.isPrescriptionRequired()
                            && reservation.getPrescriptionReviewStatus()
                            == com.tenahub.bot.entity.PrescriptionReviewStatus.UPLOAD_REQUIRED);

            StringBuilder message = new StringBuilder();
            if (awaitingPrescriptionUpload) {
                message.append("📦 <b>New grouped reservation — waiting for prescription upload</b>\n\n");
            } else {
                message.append("📦 <b>Grouped Reservation Request</b>\n\n");
            }
            message.append("🧺 <b>Group ID:</b> ").append(safeText(groupId.substring(0, Math.min(8, groupId.length())))).append("...\n\n");

            for (com.tenahub.bot.entity.MedicineReservation res : reservations) {
                message.append("💊 ").append(safeText(displayMedicine(chatId, res.getMedicineName())))
                        .append(" × ").append(res.getRequestedQuantity())
                        .append(" → #").append(res.getId()).append("\n");
            }

            message.append("\n👤 <b>Customer:</b> ").append(safeText(reservations.get(0).getCustomerName())).append("\n");
            message.append("📱 <b>Phone:</b> ").append(safeText(reservations.get(0).getCustomerPhone())).append("\n");
            if (awaitingPrescriptionUpload) {
                message.append("\nPlease wait for the customer to upload a prescription. You will get a review card after upload.");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

            if (!awaitingPrescriptionUpload) {
                List<List<Map<String, Object>>> keyboard = List.of(
                        List.of(
                                Map.of("text", "✅ Approve All", "callback_data", "approve_group_" + groupId),
                                Map.of("text", "❌ Reject All", "callback_data", "reject_group_" + groupId)
                        )
                );
                body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            }

            sendMessagePayload(body);
        } catch (Exception e) {
            log.error("sendPharmacyGroupedReservationCard error: {}", e.getMessage(), e);
            throw wrapTelegramSendError(e);
        }
    }

    public void sendPharmacyPrescriptionReviewCard(Long chatId, PrescriptionStatusResponseDTO status) {
        try {
            if (status == null) {
                System.out.println("[TG_SEND] status is null, aborting");
                return;
            }

            String url = apiUrl + "/sendMessage";
            boolean grouped = status.getReservationGroupId() != null && !status.getReservationGroupId().isBlank();
            StringBuilder message = new StringBuilder();
            message.append("🧾 <b>Prescription Review</b>\n\n");

            if (grouped) {
                String groupId = status.getReservationGroupId();
                message.append("🧺 <b>Group:</b> ")
                        .append(safeText(groupId.substring(0, Math.min(8, groupId.length()))))
                        .append("...\n");
            } else {
                message.append("🆔 <b>Reservation ID:</b> ")
                        .append(status.getReservationId())
                        .append("\n");
            }
            message.append("📌 <b>Status:</b> ")
                    .append(safeText(status.getReviewStatus()))
                    .append("\n");
            message.append("👤 <b>Uploader:</b> ")
                    .append(status.getUserId() == null || status.getUserId() == 0 ? "-" : status.getUserId())
                    .append("\n");
            if (status.getCustomerPhone() != null && !status.getCustomerPhone().isBlank()) {
                message.append("📱 <b>Phone:</b> ")
                        .append(safeText(status.getCustomerPhone()))
                        .append("\n");
            }
            String lastUploadedAt = status.getFiles() == null || status.getFiles().isEmpty()
                    ? null
                    : status.getFiles().stream()
                    .map(PrescriptionFileMetadataDTO::getUploadedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .map(value -> value.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")))
                    .orElse(null);
            if (lastUploadedAt != null) {
                message.append("🕒 <b>Uploaded:</b> ")
                        .append(safeText(lastUploadedAt))
                        .append("\n");
            }
            int fileCount = status.getFiles() == null ? 0 : status.getFiles().size();
            message.append("📎 <b>Files:</b> ")
                    .append(fileCount)
                    .append("\n");

            if (status.getRejectionReason() != null && !status.getRejectionReason().isBlank()) {
                message.append("❌ <b>Reason:</b> ")
                        .append(safeText(status.getRejectionReason()))
                        .append("\n");
            }
            if (status.getNote() != null && !status.getNote().isBlank()) {
                message.append("💬 <b>Patient note:</b> ")
                        .append(safeText(status.getNote()))
                        .append("\n");
            }

            message.append("\n💊 <b>Items</b>\n");
            if (status.getItems() != null && !status.getItems().isEmpty()) {
                for (PrescriptionStatusItemDTO item : status.getItems()) {
                    message.append("• ")
                            .append(displayMedicine(chatId, item.getMedicineName()));
                    if (item.getQuantity() != null) {
                        message.append(" ×").append(item.getQuantity());
                    }
                    message.append(" - ")
                            .append(safeText(item.getReviewStatus()))
                            .append("\n");
                }
            } else {
                message.append("No prescription-linked items found.\n");
            }

            if (status.getFiles() != null && !status.getFiles().isEmpty()) {
                message.append("\n📄 <b>Attachments</b>\n");
                for (PrescriptionFileMetadataDTO file : status.getFiles()) {
                    message.append("• ")
                            .append(safeText(file.getOriginalFilename()))
                            .append(" (")
                            .append(file.getFileSize() == null ? 0 : file.getFileSize())
                            .append(" bytes)")
                            .append("\n");
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

            String callbackSuffix = grouped
                    ? "group_" + status.getReservationGroupId()
                    : "res_" + status.getReservationId();

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "📄 View Prescription", "callback_data", "pres_files_" + callbackSuffix)
            ));

            if ("PENDING_REVIEW".equalsIgnoreCase(status.getReviewStatus())) {
                keyboard.add(List.of(
                        Map.of("text", "✅ Approve Prescription", "callback_data", "pres_approve_" + callbackSuffix),
                        Map.of("text", "❌ Reject Prescription", "callback_data", "pres_reject_" + callbackSuffix)
                ));
            }

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            System.out.println("[TG_SEND] Sending prescription review card: reservationId=" + status.getReservationId() + ", fileCount=" + fileCount + ", chatId=" + chatId);
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("[TG_SEND] Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ---------------- ADMIN PHOTO ACTIONS ---------------- */

    public Integer sendPhotoWithButtons(Long chatId, String fileId, String caption, Long registrationId) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            Map<String, Object> approveBtn = Map.of(
                    "text", "✅ Approve",
                    "callback_data", "approve_" + registrationId
            );

            Map<String, Object> rejectBtn = Map.of(
                    "text", "❌ Reject",
                    "callback_data", "reject_" + registrationId
            );

            body.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(List.of(approveBtn, rejectBtn))
            ));

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object resultObj = response.get("result");

                if (resultObj instanceof Map<?, ?> result) {
                    Object messageIdObj = result.get("message_id");
                    if (messageIdObj instanceof Number number) {
                        return number.intValue();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Telegram sendPhotoWithButtons error: " + e.getMessage());
        }

        return null;
    }

    public Integer sendPhotoWithLicenseUpdateButtons(Long chatId,
                                                     String fileId,
                                                     String caption,
                                                     Long pharmacyTelegramId) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            Map<String, Object> approveBtn = Map.of(
                    "text", "✅ Approve",
                    "callback_data", "approve_license_" + pharmacyTelegramId
            );

            Map<String, Object> rejectBtn = Map.of(
                    "text", "❌ Reject",
                    "callback_data", "reject_license_" + pharmacyTelegramId
            );

            body.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(List.of(approveBtn, rejectBtn))
            ));

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object resultObj = response.get("result");

                if (resultObj instanceof Map<?, ?> result) {
                    Object messageIdObj = result.get("message_id");
                    if (messageIdObj instanceof Number number) {
                        return number.intValue();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("sendPhotoWithLicenseUpdateButtons error: " + e.getMessage());
        }

        return null;
    }

    public void editAdminLicenseSummaryButtonClosed(Long chatId, Integer messageId, Long pharmacyId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacyId)
                        )
                )
        );
    }

    public void editAdminLicenseSummaryButtonOpen(Long chatId,
                                                  Integer messageId,
                                                  Long pharmacyId,
                                                  Integer detailMessageId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "❌ Close License",
                                        "callback_data", "close_license_" + detailMessageId + "_" + pharmacyId)
                        )
                )
        );
    }

    public void editAdminRegistrationSummaryButtonClosed(Long chatId, Integer messageId, Long registrationId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "👁 View", "callback_data", "view_reg_" + registrationId)
                        )
                )
        );
    }

            public void editAdminPharmacySummaryButtonClosed(Long chatId, Integer messageId, Long pharmacyId) {
            editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                    List.of(
                        Map.of("text", "👁 View", "callback_data", "view_admin_pharmacy_" + pharmacyId)
                    )
                )
            );
            }

            public void editAdminPharmacySummaryButtonOpen(Long chatId,
                                   Integer messageId,
                                   Long pharmacyId,
                                   Integer detailMessageId) {
            editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                    List.of(
                        Map.of("text", "❌ Close",
                            "callback_data", "close_admin_pharmacy_" + detailMessageId + "_" + pharmacyId)
                    )
                )
            );
            }

    public void editAdminRegistrationSummaryButtonOpen(Long chatId,
                                                       Integer messageId,
                                                       Long registrationId,
                                                       Integer detailMessageId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "❌ Close",
                                        "callback_data", "close_reg_" + detailMessageId + "_" + registrationId)
                        )
                )
        );
    }

    public void sendAdminPendingRegistrationsPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.PharmacyRegistration> pageData,
            int page) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "🆕 No pending registrations.");
                return;
            }

            String header = "🆕 <b>Pending Registrations</b>\n\nPage " + (page + 1) + " of " + pageData.getTotalPages();

            Map<String, Object> headerBody = new HashMap<>();
            headerBody.put("chat_id", chatId);
            headerBody.put("text", header);
            headerBody.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> navKeyboard = new ArrayList<>();
            List<Map<String, Object>> navRow = new ArrayList<>();
            navRow.add(Map.of("text", "🔄 Refresh", "callback_data", "admin_reg_page_" + page));

            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "➡️ View More", "callback_data", "admin_reg_page_" + (page + 1)));
            }

            navKeyboard.add(navRow);
            headerBody.put("reply_markup", Map.of("inline_keyboard", navKeyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", headerBody, String.class);

            for (com.tenahub.bot.entity.PharmacyRegistration reg : pageData.getContent()) {
                String text = "🆔 <b>ID:</b> " + reg.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(reg.getName()) + "\n"
                    + "🏙️ <b>City:</b> " + safe(displayLocation(chatId, reg.getCity())) + "\n"
                    + "📍 <b>Area:</b> " + safe(displayLocation(chatId, reg.getArea())) + "\n"
                        + "📞 <b>Phone:</b> " + safe(reg.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + reg.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View", "callback_data", "view_reg_" + reg.getId()),
                                        Map.of("text", "🗑 Delete", "callback_data", "delete_reg_" + reg.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingRegistrationsPage error: " + e.getMessage());
        }
    }

    public void sendAdminPharmacyManagementPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.Pharmacy> pageData,
            int page,
            String title) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "🏥 No pharmacies found.");
                return;
            }

            String header = title + "\n\nPage " + (page + 1) + " of " + pageData.getTotalPages();

            Map<String, Object> headerBody = new HashMap<>();
            headerBody.put("chat_id", chatId);
            headerBody.put("text", header);
            headerBody.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> navKeyboard = new ArrayList<>();
            List<Map<String, Object>> navRow = new ArrayList<>();
            navRow.add(Map.of("text", "🔄 Refresh", "callback_data", "admin_pharmacy_page_" + page));

            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "➡️ View More", "callback_data", "admin_pharmacy_page_" + (page + 1)));
            }

            navKeyboard.add(navRow);
            headerBody.put("reply_markup", Map.of("inline_keyboard", navKeyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", headerBody, String.class);

            for (com.tenahub.bot.entity.Pharmacy pharmacy : pageData.getContent()) {
                String text = "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                        + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId() + "\n"
                        + "📌 <b>Status:</b> " + adminPharmacyStatus(pharmacy);

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View", "callback_data", "view_admin_pharmacy_" + pharmacy.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyManagementPage error: " + e.getMessage());
        }
    }

    public Integer sendAdminPharmacyManagementDetail(Long chatId,
                                                     String text,
                                                     Long pharmacyId,
                                                     String statusActionLabel,
                                                     String statusActionCallback,
                                                     boolean hasLicense) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            if (statusActionLabel != null && !statusActionLabel.isBlank() && statusActionCallback != null && !statusActionCallback.isBlank()) {
                keyboard.add(List.of(Map.of("text", statusActionLabel, "callback_data", statusActionCallback)));
            }
            keyboard.add(List.of(Map.of("text", "🧾 Prescription Settings", "callback_data", "admin_pharmacy_prescriptions_" + pharmacyId)));
            keyboard.add(List.of(Map.of("text", "✏️ Edit Pharmacy", "callback_data", "admin_pharmacy_edit_menu_" + pharmacyId)));
            if (hasLicense) {
                keyboard.add(List.of(Map.of("text", "📄 View License", "callback_data", "admin_pharmacy_view_license_" + pharmacyId)));
            }
            keyboard.add(List.of(Map.of("text", "⬅️ Back", "callback_data", "admin_pharmacy_back_" + pharmacyId)));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object resultObj = response.get("result");
                if (resultObj instanceof Map<?, ?> result) {
                    Object messageIdObj = result.get("message_id");
                    if (messageIdObj instanceof Number number) {
                        return number.intValue();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyManagementDetail error: " + e.getMessage());
        }

        return null;
    }

    public void sendAdminPharmacyEditFieldMenu(Long chatId, Long pharmacyId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "✏️ <b>Edit Pharmacy</b>\n\nChoose a field to edit:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🏥 Name", "callback_data", "admin_pharmacy_edit_field_name_" + pharmacyId)),
                    List.of(Map.of("text", "📞 Phone", "callback_data", "admin_pharmacy_edit_field_phone_" + pharmacyId)),
                    List.of(Map.of("text", "📍 Location", "callback_data", "admin_pharmacy_edit_field_location_" + pharmacyId)),
                    List.of(Map.of("text", "🧭 Landmark", "callback_data", "admin_pharmacy_edit_field_landmark_" + pharmacyId)),
                    List.of(Map.of("text", "🌅 Open Time", "callback_data", "admin_pharmacy_edit_field_open_time_" + pharmacyId)),
                    List.of(Map.of("text", "🌙 Close Time", "callback_data", "admin_pharmacy_edit_field_close_time_" + pharmacyId)),
                    List.of(Map.of("text", "🧾 Prescription Settings", "callback_data", "admin_pharmacy_prescriptions_" + pharmacyId)),
                    List.of(Map.of("text", "📌 Approval State", "callback_data", "admin_pharmacy_edit_field_approval_" + pharmacyId)),
                    List.of(Map.of("text", "⬅️ Back", "callback_data", "admin_pharmacy_back_" + pharmacyId))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyEditFieldMenu error: " + e.getMessage());
        }
    }

    public void sendAdminPharmacyEditLocationModeMenu(Long chatId, Long pharmacyId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📍 <b>Edit Location</b>\n\nChoose location update mode:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🗺 Structured (Region/City/Area)", "callback_data", "admin_pharmacy_edit_location_structured_" + pharmacyId)),
                    List.of(Map.of("text", "📌 Exact Location", "callback_data", "admin_pharmacy_edit_location_exact_" + pharmacyId)),
                    List.of(Map.of("text", "⬅️ Back", "callback_data", "admin_pharmacy_edit_menu_" + pharmacyId))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyEditLocationModeMenu error: " + e.getMessage());
        }
    }

    public void sendAdminPharmacyApprovalStateMenu(Long chatId, Long pharmacyId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📌 <b>Set Approval State</b>\n\nChoose one:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "✅ Approved", "callback_data", "admin_pharmacy_set_state_approved_" + pharmacyId)),
                    List.of(Map.of("text", "🕒 Not Approved", "callback_data", "admin_pharmacy_set_state_not_approved_" + pharmacyId)),
                    List.of(Map.of("text", "⛔ Suspended", "callback_data", "admin_pharmacy_set_state_suspended_" + pharmacyId)),
                    List.of(Map.of("text", "⬅️ Back", "callback_data", "admin_pharmacy_edit_menu_" + pharmacyId))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminPharmacyApprovalStateMenu error: " + e.getMessage());
        }
    }

    public void sendPharmacyPrescriptionSettingCard(Long chatId,
                                                    Long medicineId,
                                                    String medicineName,
                                                    Integer quantity,
                                                    java.math.BigDecimal price,
                                                    String currency,
                                                    boolean requiresPrescription,
                                                    boolean adminMode,
                                                    Long pharmacyId) {
        try {
            String url = apiUrl + "/sendMessage";
            String priceText = price == null
                    ? "N/A"
                    : price.stripTrailingZeros().toPlainString() + " " + ((currency == null || currency.isBlank()) ? "ETB" : currency);
            String callbackData = adminMode
                    ? (requiresPrescription
                    ? "admin_inv_pres_off_" + pharmacyId + "_" + medicineId
                    : "admin_inv_pres_on_" + pharmacyId + "_" + medicineId)
                    : (requiresPrescription ? "inv_pres_off_" + medicineId : "inv_pres_on_" + medicineId);
            String buttonLabel = requiresPrescription ? "Unmark Prescription Required" : "Mark Prescription Required";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "💊 <b>Medicine Prescription Setting</b>\n\n"
                            + "🆔 <b>Medicine ID:</b> " + medicineId + "\n"
                            + (adminMode ? "🏥 <b>Pharmacy ID:</b> " + pharmacyId + "\n" : "")
                            + "💊 <b>Medicine:</b> " + displayMedicine(chatId, medicineName) + "\n"
                            + "📦 <b>Quantity:</b> " + (quantity == null ? 0 : quantity) + "\n"
                            + "💰 <b>Price:</b> " + priceText + "\n"
                            + "🧾 <b>Prescription required:</b> " + (requiresPrescription ? "Yes" : "No")
            );
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", List.of(
                    List.of(Map.of("text", buttonLabel, "callback_data", callbackData))
            )));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPharmacyPrescriptionSettingCard error: " + e.getMessage());
        }
    }

    public void sendAdminPendingLicenseUpdatesPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.Pharmacy> pageData,
            int page) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "📄 No pending license updates.");
                return;
            }

            String header = "📄 <b>Pending License Updates</b>\n\nPage " + (page + 1) + " of " + pageData.getTotalPages();

            Map<String, Object> headerBody = new HashMap<>();
            headerBody.put("chat_id", chatId);
            headerBody.put("text", header);
            headerBody.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> navKeyboard = new ArrayList<>();
            List<Map<String, Object>> navRow = new ArrayList<>();
            navRow.add(Map.of("text", "🔄 Refresh", "callback_data", "admin_license_page_" + page));

            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "➡️ View More", "callback_data", "admin_license_page_" + (page + 1)));
            }

            navKeyboard.add(navRow);
            headerBody.put("reply_markup", Map.of("inline_keyboard", navKeyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", headerBody, String.class);

            for (com.tenahub.bot.entity.Pharmacy pharmacy : pageData.getContent()) {
                String text = "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                    + "🏙️ <b>City:</b> " + safe(displayLocation(chatId, pharmacy.getCity())) + "\n"
                    + "📍 <b>Area:</b> " + safe(displayLocation(chatId, pharmacy.getArea())) + "\n"
                        + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacy.getId()))
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingLicenseUpdatesPage error: " + e.getMessage());
        }
    }

    public void sendAdminPendingRegistrations(Long chatId, List<com.tenahub.bot.entity.PharmacyRegistration> registrations) {
        try {
            if (registrations == null || registrations.isEmpty()) {
                sendMessage(chatId, "🆕 No pending registrations.");
                return;
            }

            sendMessage(chatId, "🆕 <b>Pending Registrations</b>");

            for (var reg : registrations) {
                String text = "🆔 <b>ID:</b> " + reg.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(reg.getName()) + "\n"
                    + "🏙️ <b>City:</b> " + safe(displayLocation(chatId, reg.getCity())) + "\n"
                    + "📍 <b>Area:</b> " + safe(displayLocation(chatId, reg.getArea())) + "\n"
                        + "📞 <b>Phone:</b> " + safe(reg.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + reg.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View", "callback_data", "view_reg_" + reg.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingRegistrations error: " + e.getMessage());
        }
    }

    public void sendAdminPendingLicenseUpdates(Long chatId, List<com.tenahub.bot.entity.Pharmacy> pharmacies) {
        try {
            if (pharmacies == null || pharmacies.isEmpty()) {
                sendMessage(chatId, "📄 No pending license updates.");
                return;
            }

            sendMessage(chatId, "📄 <b>Pending License Updates</b>");

            for (com.tenahub.bot.entity.Pharmacy pharmacy : pharmacies) {
                String text = "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                    + "🏙️ <b>City:</b> " + safe(displayLocation(chatId, pharmacy.getCity())) + "\n"
                    + "📍 <b>Area:</b> " + safe(displayLocation(chatId, pharmacy.getArea())) + "\n"
                        + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacy.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingLicenseUpdates error: " + e.getMessage());
        }
    }

    private String adminPharmacyStatus(com.tenahub.bot.entity.Pharmacy pharmacy) {
        if (pharmacy.isLicenseSuspended()) {
            return "Suspended";
        }
        if (pharmacy.isApproved()) {
            return "Approved ✅";
        }
        return "Not Approved";
    }

    public void sendAdminReservationOversight(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "⏳ Browse Pending", "callback_data", "admin_res_list_PENDING_0"),
                            Map.of("text", "✅ Browse Approved", "callback_data", "admin_res_list_APPROVED_0")
                    ),
                    List.of(
                            Map.of("text", "📦 Browse Fulfilled", "callback_data", "admin_res_list_FULFILLED_0"),
                            Map.of("text", "❌ Browse Rejected", "callback_data", "admin_res_list_REJECTED_0")
                    ),
                    List.of(
                            Map.of("text", "⌛ Browse Expired", "callback_data", "admin_res_list_EXPIRED_0"),
                            Map.of("text", "🚫 Browse Cancelled", "callback_data", "admin_res_list_CANCELLED_0")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminReservationOversight error: " + e.getMessage());
        }
    }

    public void sendAdminReservationListPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.MedicineReservation> pageData,
            String status,
            int page) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "📦 No " + status + " reservations found.");
                return;
            }

            String statusIcon = reservationStatusIcon(status);
            String header = statusIcon + " <b>" + status + " Reservations</b>\n\nPage "
                    + (page + 1) + " of " + pageData.getTotalPages()
                    + " (" + pageData.getTotalElements() + " total)\n\nTap a reservation to view full details:";

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            for (com.tenahub.bot.entity.MedicineReservation r : pageData.getContent()) {
                String label = statusIcon + " #" + r.getId()
                        + " — " + (r.getMedicineName() == null ? "?" : r.getMedicineName())
                        + " x" + r.getRequestedQuantity();
                keyboard.add(List.of(Map.of("text", label, "callback_data", "admin_res_open_" + r.getId())));
            }

            List<Map<String, Object>> navRow = new ArrayList<>();
            if (pageData.hasPrevious()) {
                navRow.add(Map.of("text", "◀ Prev", "callback_data", "admin_res_list_" + status + "_" + (page - 1)));
            }
            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "▶ Next", "callback_data", "admin_res_list_" + status + "_" + (page + 1)));
            }
            if (!navRow.isEmpty()) {
                keyboard.add(navRow);
            }
            keyboard.add(List.of(Map.of("text", "🔙 Back to Overview", "callback_data", "admin_res_overview")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", header);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminReservationListPage error: " + e.getMessage());
        }
    }

    public void sendAdminReservationDetail(Long chatId, String text, Long reservationId,
                                           String status, String sourceStatus) {
        try {
            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            boolean isActive = "PENDING".equals(status) || "APPROVED".equals(status);

            if (isActive) {
                List<Map<String, Object>> actionRow1 = new ArrayList<>();
                actionRow1.add(Map.of("text", "📦 Force Fulfill", "callback_data", "admin_res_fulfill_" + reservationId));
                if ("APPROVED".equals(status)) {
                    actionRow1.add(Map.of("text", "⌛ Force Expire", "callback_data", "admin_res_expire_" + reservationId));
                }
                keyboard.add(actionRow1);
                keyboard.add(List.of(
                        Map.of("text", "🚫 Force Cancel", "callback_data", "admin_res_cancel_" + reservationId)
                ));
            }

            String backData = sourceStatus != null
                    ? "admin_res_list_" + sourceStatus + "_0"
                    : "admin_res_overview";
            keyboard.add(List.of(Map.of("text", "🔙 Back", "callback_data", backData)));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminReservationDetail error: " + e.getMessage());
        }
    }

    private String reservationStatusIcon(String status) {
        if (status == null) return "📦";
        return switch (status) {
            case "PENDING"   -> "⏳";
            case "APPROVED"  -> "✅";
            case "FULFILLED" -> "📦";
            case "REJECTED"  -> "❌";
            case "EXPIRED"   -> "⌛";
            case "CANCELLED" -> "🚫";
            default          -> "📋";
        };
    }

    public void sendAdminSystemSummary(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "📦 More Reservations", "callback_data", "admin_more_reservations"),
                            Map.of("text", "🏥 More Pharmacies", "callback_data", "admin_more_pharmacies")
                    ),
                    List.of(
                            Map.of("text", "💊 More Medicines", "callback_data", "admin_more_top_medicines"),
                            Map.of("text", "⚠️ More Low Stock", "callback_data", "admin_more_low_stock")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminSystemSummary error: " + e.getMessage());
        }
    }

    public void sendRejectedRegistrationResumeMenu(Long chatId, String reason) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "❌ <b>Your pharmacy registration was rejected.</b>\n\n" +
                    "Reason: " + reason + "\n\n" +
                    "Your previous data was saved.\n" +
                    "Choose an option below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🔁 Resume Registration")),
                    List.of(Map.of("text", "🆕 Start Fresh")),
                    List.of(Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRejectedRegistrationResumeMenu error: " + e.getMessage());
        }
    }
    public void sendSearchFilterButtons(Long chatId, String medicineName, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "🔎 <b>Filters for:</b> " + displayMedicine(chatId, medicineName) + "\n" +
                "Active: <b>" + activeFilter + "</b>"
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> nearest = new HashMap<>();
        nearest.put("text", "📍 Nearest");
        nearest.put("callback_data", "filter_nearest");

        Map<String, Object> cheapest = new HashMap<>();
        cheapest.put("text", "💰 Cheapest");
        cheapest.put("callback_data", "filter_cheapest");

        Map<String, Object> rated = new HashMap<>();
        rated.put("text", "⭐ Highest Rated");
        rated.put("callback_data", "filter_rated");

        Map<String, Object> openNow = new HashMap<>();
        openNow.put("text", "🟢 Open Now");
        openNow.put("callback_data", "filter_open");

        Map<String, Object> stockOnly = new HashMap<>();
        stockOnly.put("text", "📦 In Stock Only");
        stockOnly.put("callback_data", "filter_stock");

        Map<String, Object> clear = new HashMap<>();
        clear.put("text", "❌ Clear Filters");
        clear.put("callback_data", "filter_clear");

        Map<String, Object> inlineKeyboard = new HashMap<>();
        inlineKeyboard.put("inline_keyboard", List.of(
                List.of(nearest, cheapest),
                List.of(rated, openNow),
                List.of(stockOnly),
                List.of(clear)
        ));

        body.put("reply_markup", inlineKeyboard);

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendSearchFilterButtons error: " + e.getMessage());
    }
}
public void sendSearchFilterKeyboard(Long chatId, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

        String nearestText = "Nearest";
        String cheapestText = "Cheapest";
        String highestRatedText = "Highest Rated";
        String openNowText = "Open Now";
        String inStockText = "In Stock Only";

        if (activeFilter != null) {
            String normalized = activeFilter.trim().toLowerCase();

            if (normalized.equals("nearest")) {
                nearestText = "✅ Nearest";
            } else if (normalized.equals("cheapest")) {
                cheapestText = "✅ Cheapest";
            } else if (normalized.equals("highest rated")) {
                highestRatedText = "✅ Highest Rated";
            } else if (normalized.equals("open now")) {
                openNowText = "✅ Open Now";
            } else if (normalized.equals("in stock only")) {
                inStockText = "✅ In Stock Only";
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🔎 <b>Search Filters</b>\n\n" +
                "Active: <b>" + (activeFilter == null ? "Nearest" : activeFilter) + "</b>\n" +
                "Choose a filter below."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📍 " + nearestText),
                        Map.of("text", "💰 " + cheapestText)
                ),
                List.of(
                        Map.of("text", "⭐ " + highestRatedText),
                        Map.of("text", "🟢 " + openNowText)
                ),
                List.of(
                        Map.of("text", "📦 " + inStockText),
                        Map.of("text", "❌ Clear Filters")
                ),
                List.of(
                        Map.of("text", "🔙 Back"),
                        Map.of("text", "🏠 Home")
                )
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendSearchFilterKeyboard error: " + e.getMessage());
    }
}

public void sendAlternativeMedicineSuggestions(Long chatId, String searchedMedicine, List<String> alternatives) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put(
                "text",
                "❌ <b>No exact match found for:</b> " + searchedMedicine + "\n\n" +
                "💡 <b>Did you mean one of these?</b>"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String alt : alternatives) {
            inlineKeyboard.add(List.of(
                    Map.of(
                            "text", "💊 " + alt,
                            "callback_data", "alt_med_" + alt.toLowerCase()
                    )
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "❌ Cancel", "callback_data", "alt_med_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAlternativeMedicineSuggestions error: " + e.getMessage());
    }
}
public void sendFavoritePharmacyCard(Long chatId, com.tenahub.bot.entity.Pharmacy pharmacy) {
    try {
        String url = apiUrl + "/sendMessage";

        String phoneText = (pharmacy.getPhone() == null || pharmacy.getPhone().isBlank())
                ? "N/A"
                : pharmacy.getPhone().trim();

        Double ratingValue = pharmacy.getRating();
        String ratingText = String.format("%.1f", ratingValue == null ? 0.0 : ratingValue);

        Double lat = pharmacy.getLatitude();
        Double lon = pharmacy.getLongitude();

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "❤️ <b>" + safe(pharmacy.getName()) + "</b>\n" +
            "📍 " + safe(displayLocation(chatId, pharmacy.getArea())) + ", " + safe(displayLocation(chatId, pharmacy.getCity())) + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
        List<Map<String, Object>> row1 = new ArrayList<>();

        if (lat != null && lon != null) {
            String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
            row1.add(Map.of("text", t(chatId, "card_navigate_btn"), "url", navigateUrl));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", t(chatId, "btn_remove"), "callback_data", "fav_remove_" + pharmacy.getId()));
        inlineKeyboard.add(row2);

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendFavoritePharmacyCard error: " + e.getMessage());
    }
}
public void sendRecentSearches(Long chatId, List<String> searches) {
    try {
        String url = apiUrl + "/sendMessage";

        if (searches == null || searches.isEmpty()) {
            sendMessage(chatId, "🕘 <b>Recent Searches</b>\n\nNo recent searches found.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "🕘 <b>Recent Searches</b>\n\nTap one to search again:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String medicine : searches) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "💊 " + displayMedicine(chatId, medicine), "callback_data", "recent_search_" + medicine.toLowerCase())
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "btn_home"), "callback_data", "recent_search_home")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendRecentSearches error: " + e.getMessage());
    }
}
public void sendNoResultWithAlertOption(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "❌ <b>No pharmacies found for:</b> " + displayMedicine(chatId, medicineName) + "\n\n" +
                "You can create an alert and get notified when it becomes available."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(Map.of("text", t(chatId, "btn_notify_available"), "callback_data", "alert_create_" + medicineName.toLowerCase())),
                List.of(Map.of("text", t(chatId, "btn_home"), "callback_data", "alert_home"))
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendNoResultWithAlertOption error: " + e.getMessage());
    }
}
public void sendMyAlerts(Long chatId, List<com.tenahub.bot.entity.MedicineAvailabilityAlert> alerts) {
    try {
        if (alerts == null || alerts.isEmpty()) {
            sendMessage(chatId, "🔔 <b>My Alerts</b>\n\nNo active alerts found.", "HTML");
            return;
        }

        sendMessage(
                chatId,
                "🔔 <b>My Alerts</b>\n\nManage your active alerts below.",
                "HTML"
        );

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");

        for (com.tenahub.bot.entity.MedicineAvailabilityAlert alert : alerts) {
            String locationText = (alert.getLatitude() != null && alert.getLongitude() != null)
                    ? "Saved nearby location"
                    : "Any nearby pharmacy";

            String radiusText = alert.getRadiusKm() == null
                ? "25 km"
                : String.format("%.0f km", alert.getRadiusKm());

            String cooldownText = (alert.getNotificationCooldownMinutes() == null
                ? 180
                : alert.getNotificationCooldownMinutes()) + " min";

            int sent = alert.getNotificationsSent() == null ? 0 : alert.getNotificationsSent();
            int max = alert.getMaxNotifications() == null ? 1 : alert.getMaxNotifications();

            String createdText = alert.getCreatedAt() == null
                    ? "N/A"
                    : alert.getCreatedAt().format(formatter);

            String expiresText = alert.getExpiresAt() == null
                ? "N/A"
                : alert.getExpiresAt().format(formatter);

            String text =
                    "🔔 <b>Alert</b>\n\n" +
                    "💊 <b>Medicine:</b> " + displayMedicine(chatId, alert.getMedicineName()) + "\n" +
                    "📍 <b>Location:</b> " + locationText + "\n" +
                "📏 <b>Radius:</b> " + radiusText + "\n" +
                "⏱ <b>Cooldown:</b> " + cooldownText + "\n" +
                "🔁 <b>Notifications:</b> " + sent + "/" + max + "\n" +
                "🕒 <b>Created:</b> " + createdText + "\n" +
                "⌛ <b>Expires:</b> " + expiresText;

            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of(
                    "inline_keyboard",
                    List.of(
                            List.of(
                                    Map.of("text", t(chatId, "btn_search_now"), "callback_data", "alert_search_" + alert.getMedicineName().toLowerCase()),
                                    Map.of("text", t(chatId, "btn_remove_alert"), "callback_data", "alert_remove_" + alert.getId())
                            )
                    )
            ));

            restTemplate.postForObject(url, body, String.class);
        }

        Map<String, Object> footerBody = new HashMap<>();
        footerBody.put("chat_id", chatId);
        footerBody.put("text", "⚙️ Alert actions");
        footerBody.put("reply_markup", Map.of(
                "inline_keyboard",
                List.of(
                        List.of(Map.of("text", t(chatId, "btn_remove_all_alerts"), "callback_data", "alert_remove_all")),
                        List.of(Map.of("text", t(chatId, "btn_home"), "callback_data", "alert_home"))
                )
        ));

        restTemplate.postForObject(apiUrl + "/sendMessage", footerBody, String.class);

    } catch (Exception e) {
        System.out.println("sendMyAlerts error: " + e.getMessage());
    }
}

public void sendAlternativeMedicineSuggestionsWithNotify(Long chatId, String searchedMedicine, MedicineSuggestionResult suggestionResult) {
    try {
        String url = apiUrl + "/sendMessage";
        List<String> typoSuggestions = suggestionResult == null ? List.of() : suggestionResult.typoSuggestions();
        List<String> alternativeSuggestions = suggestionResult == null ? List.of() : suggestionResult.alternativeSuggestions();

        Map<String, Object> body = new HashMap<>();
        StringBuilder text = new StringBuilder(t(chatId, "medicine_suggestion_no_exact", displayMedicine(chatId, searchedMedicine)));
        if (!typoSuggestions.isEmpty()) {
            text.append("\n\n").append(t(chatId, "medicine_suggestion_did_you_mean"));
        }
        if (!alternativeSuggestions.isEmpty()) {
            text.append(typoSuggestions.isEmpty() ? "\n\n" : "\n")
                    .append(t(chatId, "medicine_suggestion_alternatives_title"));
        }

        body.put("chat_id", chatId);
        body.put("text", text.toString());
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String alt : typoSuggestions) {
            inlineKeyboard.add(List.of(
                Map.of("text", "💊 " + displayMedicine(chatId, alt), "callback_data", "alt_med_" + alt.toLowerCase())
            ));
        }

        for (String alt : alternativeSuggestions) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "💡 " + displayMedicine(chatId, alt), "callback_data", "alt_med_" + alt.toLowerCase())
            ));
        }

        inlineKeyboard.add(List.of(
            Map.of("text", t(chatId, "medicine_suggestion_notify_for", displayMedicine(chatId, searchedMedicine)),
                        "callback_data", "alert_create_" + searchedMedicine.toLowerCase())
        ));

        inlineKeyboard.add(List.of(
                Map.of("text", t(chatId, "btn_cancel"), "callback_data", "alt_med_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAlternativeMedicineSuggestionsWithNotify error: " + e.getMessage());
    }
}
public void sendNoMedicineFoundWithNotify(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
    body.put("text", t(chatId, "medicine_no_pharmacies_found", displayMedicine(chatId, medicineName)));
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
            Map.of("text", t(chatId, "btn_notify_available"), "callback_data", "alert_create_" + medicineName.toLowerCase())
                ),
                List.of(
            Map.of("text", t(chatId, "btn_home"), "callback_data", "alert_home")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendNoMedicineFoundWithNotify error: " + e.getMessage());
    }
}

public void sendAllResultsOutOfStockNotice(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
            "⚠️ <b>" + displayMedicine(chatId, medicineName) + "</b> was found, but nearby pharmacies are currently out of stock.\n\n" +
                "🔔 You can create an alert and get notified when stock is updated."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of("text", "🔔 Notify Me", "callback_data", "alert_create_" + medicineName.toLowerCase())
                ),
                List.of(
                        Map.of("text", "🏠 Home", "callback_data", "alert_home")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAllResultsOutOfStockNotice error: " + e.getMessage());
    }
}
}

