package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/notifications", "/proxyapi/api/pharmacy/notifications"})
public class PharmacyMiniAppNotificationsController {

    private final PharmacyNotificationService pharmacyNotificationService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyNotificationService.list(pharmacyTelegramId, unreadOnly));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(Map.of("unreadCount", pharmacyNotificationService.unreadCount(pharmacyTelegramId)));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markRead(
            @PathVariable Long notificationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(pharmacyNotificationService.markRead(pharmacyTelegramId, notificationId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            int updated = pharmacyNotificationService.markAllRead(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("success", true, "updatedCount", updated));
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
