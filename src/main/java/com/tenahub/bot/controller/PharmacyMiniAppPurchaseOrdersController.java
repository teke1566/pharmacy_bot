package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyPurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/purchase-orders", "/proxyapi/api/pharmacy/purchase-orders"})
public class PharmacyMiniAppPurchaseOrdersController {

    private final PharmacyPurchaseOrderService pharmacyPurchaseOrderService;
    private final MiniAppActorResolver miniAppActorResolver;
    private final PharmacyAuthorizationService pharmacyAuthorizationService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "status", required = false) String status) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PURCHASE_ORDER_VIEW);
            return ResponseEntity.ok(pharmacyPurchaseOrderService.list(actor.getPharmacyTelegramId(), status));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{purchaseOrderId}")
    public ResponseEntity<?> get(
            @PathVariable Long purchaseOrderId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PURCHASE_ORDER_VIEW);
            return ResponseEntity.ok(pharmacyPurchaseOrderService.get(actor.getPharmacyTelegramId(), purchaseOrderId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PURCHASE_ORDER_CREATE);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(pharmacyPurchaseOrderService.create(actor.getPharmacyTelegramId(), body));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{purchaseOrderId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long purchaseOrderId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PURCHASE_ORDER_APPROVE);
            String status = body != null && body.get("status") != null ? body.get("status").toString() : null;
            return ResponseEntity.ok(pharmacyPurchaseOrderService.updateStatus(
                    actor.getPharmacyTelegramId(), purchaseOrderId, status));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{purchaseOrderId}/receive")
    public ResponseEntity<?> receive(
            @PathVariable Long purchaseOrderId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PURCHASE_ORDER_RECEIVE);
            return ResponseEntity.ok(pharmacyPurchaseOrderService.receive(
                    actor.getPharmacyTelegramId(), purchaseOrderId, body));
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
