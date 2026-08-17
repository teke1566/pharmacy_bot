package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyMiniAppMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy", "/proxyapi/api/pharmacy"})
public class PharmacyMiniAppPhotosController {

    private final PharmacyMiniAppMediaService pharmacyMiniAppMediaService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping("/photos")
    public ResponseEntity<?> listPharmacyPhotos(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyMiniAppMediaService.listPharmacyPhotos(pharmacyTelegramId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addPharmacyPhoto(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(pharmacyMiniAppMediaService.addPharmacyPhoto(pharmacyTelegramId, file, caption));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/photos/{photoId}/main")
    public ResponseEntity<?> setMainPharmacyPhoto(
            @PathVariable Long photoId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyMiniAppMediaService.setMainPharmacyPhoto(pharmacyTelegramId, photoId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<?> removePharmacyPhoto(
            @PathVariable Long photoId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyMiniAppMediaService.removePharmacyPhoto(pharmacyTelegramId, photoId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Photo removed").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/inventory/{itemId}/photos")
    public ResponseEntity<?> listMedicinePhotos(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyMiniAppMediaService.listMedicinePhotos(pharmacyTelegramId, itemId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping(value = "/inventory/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addMedicinePhoto(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(pharmacyMiniAppMediaService.addMedicinePhoto(pharmacyTelegramId, itemId, file, caption));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/inventory/{itemId}/photos/{photoId}/main")
    public ResponseEntity<?> setMainMedicinePhoto(
            @PathVariable Long itemId,
            @PathVariable Long photoId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyMiniAppMediaService.setMainMedicinePhoto(pharmacyTelegramId, itemId, photoId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/inventory/{itemId}/photos/{photoId}")
    public ResponseEntity<?> removeMedicinePhoto(
            @PathVariable Long itemId,
            @PathVariable Long photoId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            pharmacyMiniAppMediaService.removeMedicinePhoto(pharmacyTelegramId, itemId, photoId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Photo removed").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("does not belong")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
