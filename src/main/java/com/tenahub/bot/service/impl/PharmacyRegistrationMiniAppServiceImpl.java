package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyMiniAppRegistrationStatusDTO;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.service.PharmacyRegistrationMiniAppService;
import com.tenahub.bot.service.RegistrationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PharmacyRegistrationMiniAppServiceImpl implements PharmacyRegistrationMiniAppService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final RegistrationService registrationService;
    private final TelegramClient telegramClient;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    @Override
    public PharmacyMiniAppRegistrationStatusDTO getStatus(Long telegramId) {
        if (telegramId == null) {
            throw new IllegalArgumentException("Telegram identity is required");
        }
        if (registrationService.isRegisteredPharmacy(telegramId)) {
            return PharmacyMiniAppRegistrationStatusDTO.builder().status("approved").build();
        }
        PharmacyRegistration latest = registrationService.getLatest(telegramId);
        if (latest == null) {
            return PharmacyMiniAppRegistrationStatusDTO.builder().status("none").build();
        }
        if ("PENDING".equalsIgnoreCase(latest.getStatus())) {
            return PharmacyMiniAppRegistrationStatusDTO.builder()
                    .status("pending")
                    .registrationId(latest.getId())
                    .build();
        }
        if ("REJECTED".equalsIgnoreCase(latest.getStatus())) {
            return toStatus(latest);
        }
        return PharmacyMiniAppRegistrationStatusDTO.builder().status("none").build();
    }

    @Override
    public PharmacyMiniAppRegistrationStatusDTO submit(
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
            MultipartFile licenseFile) {
        if (telegramId == null) {
            throw new IllegalArgumentException("Telegram identity is required");
        }
        if (registrationService.isRegisteredPharmacy(telegramId)) {
            throw new IllegalStateException("You already have a registered pharmacy");
        }
        if (registrationService.exists(telegramId)) {
            throw new IllegalStateException("Your pharmacy registration is already under review");
        }

        String trimmedName = requireText(name, "Pharmacy name is required");
        String trimmedPhone = requireText(phone, "Phone is required").replaceAll("\\s+", "");
        if (!PHONE_PATTERN.matcher(trimmedPhone).matches()) {
            throw new IllegalArgumentException("Enter a valid phone number");
        }
        String trimmedMedicines = requireText(medicines, "Select at least one medicine");
        String trimmedOpen = normalizeTime(openTime, "Opening time is required");
        String trimmedClose = normalizeTime(closeTime, "Closing time is required");
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Pharmacy location is required");
        }
        LocalDate expiry = parseExpiry(licenseExpiryDate);
        byte[] licenseBytes = readLicenseBytes(licenseFile);

        String resolvedCity = blankTo(city, "Unknown");
        String resolvedArea = blankTo(area, "Unknown Area");

        Long registrationId = registrationService.register(
                trimmedName,
                resolvedCity,
                resolvedArea,
                trimmedPhone,
                trimmedMedicines,
                trimmedOpen,
                trimmedClose,
                telegramId);
        registrationService.saveLocation(telegramId, latitude, longitude, formattedAddress, plusCode, landmark);
        registrationService.saveLocationDetails(telegramId, formattedAddress, landmark, plusCode);
        registrationService.saveLicenseExpiryDate(telegramId, expiry);

        PharmacyRegistration reg = registrationService.getRegistration(registrationId);
        String caption = buildAdminCaption(reg);
        String filename = licenseFile.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "pharmacy-license.jpg";
        }
        String fileId = telegramClient.sendPhotoBytesWithButtons(
                adminChatId,
                licenseBytes,
                filename,
                caption,
                registrationId);
        if (fileId == null || fileId.isBlank()) {
            registrationService.deletePendingByTelegramId(telegramId);
            throw new IllegalStateException("Could not send license to admin. Please try again.");
        }
        registrationService.saveLicense(telegramId, fileId);
        return PharmacyMiniAppRegistrationStatusDTO.builder()
                .status("pending")
                .registrationId(registrationId)
                .build();
    }

    @Override
    public PharmacyMiniAppRegistrationStatusDTO restart(Long telegramId) {
        if (telegramId == null) {
            throw new IllegalArgumentException("Telegram identity is required");
        }
        if (registrationService.isRegisteredPharmacy(telegramId)) {
            throw new IllegalStateException("You already have a registered pharmacy");
        }
        if (registrationService.exists(telegramId)) {
            throw new IllegalStateException("Your pharmacy registration is already under review");
        }
        return PharmacyMiniAppRegistrationStatusDTO.builder().status("none").build();
    }

    private PharmacyMiniAppRegistrationStatusDTO toStatus(PharmacyRegistration reg) {
        String status = reg.getStatus() == null ? "none" : reg.getStatus().trim().toLowerCase(Locale.ROOT);
        if ("approved".equals(status)) {
            status = "approved";
        } else if ("pending".equals(status)) {
            status = "pending";
        } else if ("rejected".equals(status)) {
            status = "rejected";
        } else {
            status = "none";
        }
        return PharmacyMiniAppRegistrationStatusDTO.builder()
                .status(status)
                .registrationId(reg.getId())
                .rejectionReason(reg.getRejectionReason())
                .name(reg.getName())
                .city(reg.getCity())
                .area(reg.getArea())
                .phone(reg.getPhone())
                .medicines(reg.getMedicines())
                .openTime(reg.getOpenTime())
                .closeTime(reg.getCloseTime())
                .latitude(reg.getLatitude())
                .longitude(reg.getLongitude())
                .formattedAddress(reg.getFormattedAddress())
                .landmark(reg.getLandmark())
                .plusCode(reg.getPlusCode())
                .licenseExpiryDate(reg.getLicenseExpiryDate() == null ? null : reg.getLicenseExpiryDate().toString())
                .build();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeTime(String value, String message) {
        String trimmed = requireText(value, message);
        if (trimmed.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$")) {
            String[] parts = trimmed.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new IllegalArgumentException(message);
            }
            return hour + ":" + (minute < 10 ? "0" + minute : String.valueOf(minute));
        }
        throw new IllegalArgumentException(message);
    }

    private static LocalDate parseExpiry(String raw) {
        String trimmed = requireText(raw, "License expiry date is required");
        try {
            LocalDate expiry = LocalDate.parse(trimmed);
            if (expiry.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("License expiry must be today or in the future");
            }
            return expiry;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("License expiry must be YYYY-MM-DD");
        }
    }

    private static byte[] readLicenseBytes(MultipartFile licenseFile) {
        if (licenseFile == null || licenseFile.isEmpty()) {
            throw new IllegalArgumentException("License photo is required");
        }
        String contentType = licenseFile.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("License must be a photo");
        }
        try {
            byte[] bytes = licenseFile.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("License photo is required");
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read license photo");
        }
    }

    private String buildAdminCaption(PharmacyRegistration reg) {
        return "🆕 <b>New Pharmacy Registration</b>\n\n"
                + "🏥 <b>Name:</b> " + safe(reg.getName()) + "\n"
                + "🏙️ <b>City:</b> " + telegramClient.displayLocation(adminChatId, reg.getCity()) + "\n"
                + "📍 <b>Area:</b> " + telegramClient.displayLocation(adminChatId, reg.getArea()) + "\n"
                + "📞 <b>Phone:</b> " + safe(reg.getPhone()) + "\n"
                + "💊 <b>Medicines:</b> " + safe(reg.getMedicines()) + "\n"
                + "🕒 <b>Open:</b> " + safe(reg.getOpenTime()) + "\n"
                + "🌙 <b>Close:</b> " + safe(reg.getCloseTime()) + "\n"
                + "📅 <b>License Expiry:</b> " + reg.getLicenseExpiryDate() + "\n"
                + "📌 <b>Latitude:</b> " + reg.getLatitude() + "\n"
                + "📌 <b>Longitude:</b> " + reg.getLongitude() + "\n"
                + "🆔 <b>Telegram ID:</b> " + reg.getTelegramId();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
