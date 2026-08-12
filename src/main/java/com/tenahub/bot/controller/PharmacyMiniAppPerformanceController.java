package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/performance", "/proxyapi/api/pharmacy/performance"})
public class PharmacyMiniAppPerformanceController {

    private final PharmacyPerformanceService pharmacyPerformanceService;
    private final InventoryService inventoryService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> getPerformance(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET performance, pharmacyTelegramId={}", pharmacyTelegramId);
            String card = pharmacyPerformanceService.buildPerformanceCard(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("performance", card));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/demand-insights")
    public ResponseEntity<?> getDemandInsights(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String insights = inventoryService.getDemandInsights(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("demandInsights", insights));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/restock-suggestions")
    public ResponseEntity<?> getRestockSuggestions(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String suggestions = inventoryService.getAdvancedRestockSuggestions(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("restockSuggestions", suggestions));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private ResponseEntity<?> forbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
