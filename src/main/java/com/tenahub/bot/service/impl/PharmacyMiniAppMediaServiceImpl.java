package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MiniAppMedicinePhotosDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MiniAppPhotoDTO;
import com.tenahub.bot.entity.MedicinePhoto;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyPhoto;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicinePhotoService;
import com.tenahub.bot.service.PharmacyMiniAppMediaService;
import com.tenahub.bot.service.PharmacyPhotoService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.TelegramClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class PharmacyMiniAppMediaServiceImpl implements PharmacyMiniAppMediaService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final PharmacyPhotoService pharmacyPhotoService;
    private final MedicinePhotoService medicinePhotoService;
    private final PharmacyService pharmacyService;
    private final TelegramClient telegramClient;
    private final long adminChatId;

    public PharmacyMiniAppMediaServiceImpl(
            PharmacyRepository pharmacyRepository,
            PharmacyInventoryRepository inventoryRepository,
            PharmacyPhotoService pharmacyPhotoService,
            MedicinePhotoService medicinePhotoService,
            PharmacyService pharmacyService,
            TelegramClient telegramClient,
            @Value("${tenahub.admin.chat-id:0}") long adminChatId) {
        this.pharmacyRepository = pharmacyRepository;
        this.inventoryRepository = inventoryRepository;
        this.pharmacyPhotoService = pharmacyPhotoService;
        this.medicinePhotoService = medicinePhotoService;
        this.pharmacyService = pharmacyService;
        this.telegramClient = telegramClient;
        this.adminChatId = adminChatId;
    }

    @Override
    public MiniAppOperationResponseDTO submitLicenseUpdate(Long telegramId, MultipartFile licenseFile, String licenseExpiryDate) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        if ("PENDING".equalsIgnoreCase(pharmacy.getLicenseUpdateStatus())) {
            throw new RuntimeException("A license update is already pending admin approval");
        }
        LocalDate expiry = parseExpiry(licenseExpiryDate);
        byte[] bytes = readImageBytes(licenseFile, "License photo is required");
        String filename = filenameOf(licenseFile, "pharmacy-license.jpg");

        String fileId = telegramClient.sendPhotoBytes(
                telegramId,
                bytes,
                filename,
                "📄 License update submitted from Mini App");
        if (fileId == null || fileId.isBlank()) {
            throw new RuntimeException("Could not upload license photo. Please try again.");
        }

        pharmacyService.savePendingLicenseUpdate(telegramId, fileId, expiry);

        String caption = "🔄 <b>License Update Request</b>\n\n"
                + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                + "🏙️ <b>City:</b> " + telegramClient.displayLocation(adminChatId, pharmacy.getCity()) + "\n"
                + "📍 <b>Area:</b> " + telegramClient.displayLocation(adminChatId, pharmacy.getArea()) + "\n"
                + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                + "💊 <b>Medicines:</b> " + safe(pharmacy.getMedicines()) + "\n"
                + "🕒 <b>Open:</b> " + pharmacy.getOpenTime() + "\n"
                + "🌙 <b>Close:</b> " + pharmacy.getCloseTime() + "\n"
                + "📅 <b>License Expiry:</b> " + expiry + "\n"
                + "📌 <b>Latitude:</b> " + pharmacy.getLatitude() + "\n"
                + "📌 <b>Longitude:</b> " + pharmacy.getLongitude() + "\n"
                + "🆔 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

        telegramClient.sendPhotoWithLicenseUpdateButtons(adminChatId, fileId, caption, telegramId);
        return MiniAppOperationResponseDTO.builder()
                .success(true)
                .message("License update submitted for admin approval")
                .build();
    }

    @Override
    public MiniAppPharmacyPhotosDTO listPharmacyPhotos(Long telegramId) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        pharmacyPhotoService.ensureLegacyPhotoImported(pharmacy.getId());
        return MiniAppPharmacyPhotosDTO.builder()
                .pharmacyId(pharmacy.getId())
                .pharmacyName(pharmacy.getName())
                .photos(pharmacyPhotoService.listByPharmacyId(pharmacy.getId()).stream()
                        .map(this::toPharmacyPhotoDto)
                        .toList())
                .build();
    }

    @Override
    public MiniAppPhotoDTO addPharmacyPhoto(Long telegramId, MultipartFile file, String caption) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        byte[] bytes = readImageBytes(file, "Photo is required");
        String fileId = telegramClient.sendPhotoBytes(
                telegramId,
                bytes,
                filenameOf(file, "pharmacy-photo.jpg"),
                caption);
        if (fileId == null || fileId.isBlank()) {
            throw new RuntimeException("Could not upload photo. Please try again.");
        }
        PharmacyPhoto saved = pharmacyPhotoService.addPhoto(pharmacy.getId(), fileId, blankToNull(caption));
        return toPharmacyPhotoDto(saved);
    }

    @Override
    public MiniAppPhotoDTO setMainPharmacyPhoto(Long telegramId, Long photoId) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        return toPharmacyPhotoDto(pharmacyPhotoService.setMainPhoto(pharmacy.getId(), photoId));
    }

    @Override
    public void removePharmacyPhoto(Long telegramId, Long photoId) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        pharmacyPhotoService.removePhoto(pharmacy.getId(), photoId);
    }

    @Override
    public MiniAppMedicinePhotosDTO listMedicinePhotos(Long telegramId, Long itemId) {
        PharmacyInventory item = requireOwnedInventory(telegramId, itemId);
        return MiniAppMedicinePhotosDTO.builder()
                .medicineId(item.getId())
                .pharmacyId(item.getPharmacyId())
                .medicineName(item.getMedicineName())
                .photos(medicinePhotoService.listByMedicineId(item.getId()).stream()
                        .map(this::toMedicinePhotoDto)
                        .toList())
                .build();
    }

    @Override
    public MiniAppPhotoDTO addMedicinePhoto(Long telegramId, Long itemId, MultipartFile file, String caption) {
        PharmacyInventory item = requireOwnedInventory(telegramId, itemId);
        byte[] bytes = readImageBytes(file, "Photo is required");
        String fileId = telegramClient.sendPhotoBytes(
                telegramId,
                bytes,
                filenameOf(file, "medicine-photo.jpg"),
                caption);
        if (fileId == null || fileId.isBlank()) {
            throw new RuntimeException("Could not upload photo. Please try again.");
        }
        MedicinePhoto saved = medicinePhotoService.addPhoto(item.getId(), fileId, blankToNull(caption));
        return toMedicinePhotoDto(saved);
    }

    @Override
    public MiniAppPhotoDTO setMainMedicinePhoto(Long telegramId, Long itemId, Long photoId) {
        PharmacyInventory item = requireOwnedInventory(telegramId, itemId);
        return toMedicinePhotoDto(medicinePhotoService.setMainPhoto(item.getId(), photoId));
    }

    @Override
    public void removeMedicinePhoto(Long telegramId, Long itemId, Long photoId) {
        PharmacyInventory item = requireOwnedInventory(telegramId, itemId);
        medicinePhotoService.removePhoto(item.getId(), photoId);
    }

    private Pharmacy requirePharmacy(Long telegramId) {
        return pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private PharmacyInventory requireOwnedInventory(Long telegramId, Long itemId) {
        Pharmacy pharmacy = requirePharmacy(telegramId);
        if (itemId == null) {
            throw new RuntimeException("Medicine is required");
        }
        PharmacyInventory item = inventoryRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Medicine not found"));
        if (item.getPharmacyId() == null || !item.getPharmacyId().equals(pharmacy.getId())) {
            throw new RuntimeException("Medicine does not belong to this pharmacy");
        }
        return item;
    }

    private MiniAppPhotoDTO toPharmacyPhotoDto(PharmacyPhoto photo) {
        return MiniAppPhotoDTO.builder()
                .photoId(photo.getId())
                .fileId(photo.getFileId())
                .mainPhoto(photo.isMainPhoto())
                .sortOrder(photo.getSortOrder())
                .caption(photo.getCaption())
                .build();
    }

    private MiniAppPhotoDTO toMedicinePhotoDto(MedicinePhoto photo) {
        return MiniAppPhotoDTO.builder()
                .photoId(photo.getId())
                .fileId(photo.getTelegramFileId())
                .mainPhoto(photo.isMainPhoto())
                .sortOrder(photo.getSortOrder())
                .caption(photo.getCaption())
                .build();
    }

    private static LocalDate parseExpiry(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("License expiry date is required");
        }
        try {
            LocalDate expiry = LocalDate.parse(raw.trim());
            if (expiry.isBefore(LocalDate.now())) {
                throw new RuntimeException("License expiry must be today or in the future");
            }
            return expiry;
        } catch (DateTimeParseException e) {
            throw new RuntimeException("License expiry must be YYYY-MM-DD");
        }
    }

    private static byte[] readImageBytes(MultipartFile file, String missingMessage) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException(missingMessage);
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            throw new RuntimeException("File must be a photo");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new RuntimeException(missingMessage);
            }
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException("Could not read photo");
        }
    }

    private static String filenameOf(MultipartFile file, String fallback) {
        String name = file == null ? null : file.getOriginalFilename();
        return name == null || name.isBlank() ? fallback : name;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
