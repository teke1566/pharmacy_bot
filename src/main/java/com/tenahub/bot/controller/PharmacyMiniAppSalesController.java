package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacySalesService;
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
@RequestMapping({"/api/pharmacy/sales", "/proxyapi/api/pharmacy/sales"})
public class PharmacyMiniAppSalesController {

    private final PharmacySalesService pharmacySalesService;
    private final MiniAppActorResolver miniAppActorResolver;
    private final PharmacyAuthorizationService pharmacyAuthorizationService;

    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "period", required = false, defaultValue = "daily") String period) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SALES_VIEW);
            return ResponseEntity.ok(pharmacySalesService.summary(actor.getPharmacyTelegramId(), period));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping
    public ResponseEntity<?> history(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "period", required = false, defaultValue = "daily") String period) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SALES_VIEW);
            return ResponseEntity.ok(pharmacySalesService.history(actor.getPharmacyTelegramId(), period));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private PharmacyActor actor(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyActor(headerValue, paramValue);
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && (e.getMessage().contains("does not belong")
                || e.getMessage().toLowerCase().contains("missing permission"))
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
