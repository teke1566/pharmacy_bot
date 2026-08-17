package com.tenahub.bot.controller;

import com.tenahub.bot.dto.*;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/pricing", "/proxyapi/api/pharmacy/pricing"})
public class PharmacyMiniAppPricingController {

    private final PharmacyPricingService pharmacyPricingService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.overview(actor(headerPharmacyId, paramPharmacyId)));
    }

    @GetMapping("/items")
    public ResponseEntity<?> items(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.listItems(actor(headerPharmacyId, paramPharmacyId)));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<?> item(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.getItem(actor(headerPharmacyId, paramPharmacyId), itemId));
    }

    @GetMapping("/items/{itemId}/history")
    public ResponseEntity<?> history(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.history(actor(headerPharmacyId, paramPharmacyId), itemId));
    }

    @GetMapping("/history")
    public ResponseEntity<?> recentHistory(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.recentHistory(actor(headerPharmacyId, paramPharmacyId)));
    }

    @PostMapping("/items/{itemId}/changes")
    public ResponseEntity<?> submitChange(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody PriceChangeSubmitRequestDTO body) {
        return ok(() -> pharmacyPricingService.submitChange(actor(headerPharmacyId, paramPharmacyId), itemId, body));
    }

    @GetMapping("/price-change-requests")
    public ResponseEntity<?> listRequests(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "status", required = false) String status) {
        return ok(() -> pharmacyPricingService.listRequests(actor(headerPharmacyId, paramPharmacyId), status));
    }

    @PostMapping("/price-change-requests/{requestId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long requestId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.approveRequest(actor(headerPharmacyId, paramPharmacyId), requestId));
    }

    @PostMapping("/price-change-requests/{requestId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long requestId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ok(() -> pharmacyPricingService.rejectRequest(actor(headerPharmacyId, paramPharmacyId), requestId, reason));
    }

    @PostMapping("/bulk/preview")
    public ResponseEntity<?> bulkPreview(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody BulkPricePreviewRequestDTO body) {
        return ok(() -> pharmacyPricingService.bulkPreview(actor(headerPharmacyId, paramPharmacyId), body));
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkApply(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody BulkPricePreviewRequestDTO body) {
        return ok(() -> pharmacyPricingService.bulkApply(actor(headerPharmacyId, paramPharmacyId), body));
    }

    @GetMapping("/promotions")
    public ResponseEntity<?> promotions(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.listPromotions(actor(headerPharmacyId, paramPharmacyId)));
    }

    @PostMapping("/promotions")
    public ResponseEntity<?> createPromotion(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody PromotionCreateRequestDTO body) {
        return ok(() -> pharmacyPricingService.createPromotion(actor(headerPharmacyId, paramPharmacyId), body));
    }

    @PostMapping("/promotions/{promotionId}/deactivate")
    public ResponseEntity<?> deactivatePromotion(
            @PathVariable Long promotionId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        return ok(() -> pharmacyPricingService.deactivatePromotion(actor(headerPharmacyId, paramPharmacyId), promotionId));
    }

    private PharmacyActor actor(Long header, Long param) {
        return miniAppActorResolver.requirePharmacyActor(header, param);
    }

    private ResponseEntity<?> ok(SupplierCall call) {
        try {
            return ResponseEntity.ok(call.get());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            HttpStatus status = HttpStatus.BAD_REQUEST;
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("does not belong") || msg.contains("missing permission")) {
                status = HttpStatus.FORBIDDEN;
            } else if (msg.contains("not found")) {
                status = HttpStatus.NOT_FOUND;
            } else if (msg.contains("changed by another user")) {
                status = HttpStatus.CONFLICT;
            }
            return ResponseEntity.status(status)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        }
    }

    @FunctionalInterface
    private interface SupplierCall {
        Object get();
    }
}
