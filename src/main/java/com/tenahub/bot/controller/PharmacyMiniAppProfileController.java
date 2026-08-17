package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyMiniAppProfileDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyMiniAppMediaService;
import com.tenahub.bot.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/profile", "/proxyapi/api/pharmacy/profile"})
public class PharmacyMiniAppProfileController {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyService pharmacyService;
    private final PharmacyMiniAppMediaService pharmacyMiniAppMediaService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET profile, pharmacyTelegramId={}", pharmacyTelegramId);
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                    .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
            return ResponseEntity.ok(toDTO(pharmacy));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    @PutMapping("/phone")
    public ResponseEntity<?> updatePhone(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, String> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyService.updatePhone(pharmacyTelegramId, body.get("phone"));
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Phone updated").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/hours")
    public ResponseEntity<?> updateHours(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, String> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyService.updateHours(pharmacyTelegramId, body.get("openTime"), body.get("closeTime"));
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Hours updated").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/location")
    public ResponseEntity<?> updateLocation(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyService.updateLocation(
                    pharmacyTelegramId,
                    toDouble(body.get("latitude")),
                    toDouble(body.get("longitude")),
                    toText(body.get("city")),
                    toText(body.get("area")),
                    toText(body.get("formattedAddress")),
                    toText(body.get("landmark")));
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Location updated").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping(value = "/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateLicense(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam("license") MultipartFile licenseFile,
            @RequestParam("licenseExpiryDate") String licenseExpiryDate) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyMiniAppMediaService.submitLicenseUpdate(
                    pharmacyTelegramId, licenseFile, licenseExpiryDate));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/temporary-closure")
    public ResponseEntity<?> setTemporaryClosure(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String reason = body.get("reason") != null ? body.get("reason").toString() : null;
            int durationHours = body.get("durationHours") instanceof Number n ? n.intValue() : 24;
            pharmacyService.setTemporaryClosure(pharmacyTelegramId, reason, durationHours);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Pharmacy temporarily closed").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/temporary-closure")
    public ResponseEntity<?> clearTemporaryClosure(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyService.clearTemporaryClosure(pharmacyTelegramId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Pharmacy reopened").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private PharmacyMiniAppProfileDTO toDTO(Pharmacy p) {
        return PharmacyMiniAppProfileDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .city(p.getCity())
                .area(p.getArea())
                .phone(p.getPhone())
                .latitude(p.getLatitude() != null ? p.getLatitude() : 0)
                .longitude(p.getLongitude() != null ? p.getLongitude() : 0)
                .formattedAddress(p.getFormattedAddress())
                .landmark(p.getLandmark())
                .rating(p.getRating())
                .approved(p.isApproved())
                .openTime(p.getOpenTime())
                .closeTime(p.getCloseTime())
                .temporarilyClosed(p.isTemporarilyClosed())
                .temporaryClosureReason(p.getTemporaryClosureReason())
                .temporaryClosedUntil(p.getTemporaryClosedUntil())
                .lastInventoryUpdate(p.getLastInventoryUpdate())
                .photoFileId(p.getPhotoFileId())
                .licenseSuspended(p.isLicenseSuspended())
                .licenseExpiryDate(p.getLicenseExpiryDate())
                .licenseUpdateStatus(p.getLicenseUpdateStatus())
                .pendingLicenseExpiryDate(p.getPendingLicenseExpiryDate())
                .build();
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Double.parseDouble(text);
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private ResponseEntity<?> forbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }

    private ResponseEntity<?> error(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
