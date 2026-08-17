package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/dashboard", "/proxyapi/api/pharmacy/dashboard"})
public class PharmacyMiniAppDashboardController {

    private final PharmacyDashboardService pharmacyDashboardService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> getDashboard(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = miniAppActorResolver.requirePharmacyTelegramId(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET dashboard, pharmacyTelegramId={}", pharmacyTelegramId);
            return ResponseEntity.ok(pharmacyDashboardService.getDashboard(pharmacyTelegramId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        }
    }
}
