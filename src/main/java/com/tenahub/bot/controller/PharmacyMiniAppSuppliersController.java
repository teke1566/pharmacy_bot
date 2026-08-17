package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacySupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/suppliers", "/proxyapi/api/pharmacy/suppliers"})
public class PharmacyMiniAppSuppliersController {

    private final PharmacySupplierService pharmacySupplierService;
    private final MiniAppActorResolver miniAppActorResolver;
    private final PharmacyAuthorizationService pharmacyAuthorizationService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "search", required = false) String search) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SUPPLIER_VIEW);
            return ResponseEntity.ok(pharmacySupplierService.list(actor.getPharmacyTelegramId(), search));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<?> get(
            @PathVariable Long supplierId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SUPPLIER_VIEW);
            return ResponseEntity.ok(pharmacySupplierService.get(actor.getPharmacyTelegramId(), supplierId));
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
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SUPPLIER_CREATE);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(pharmacySupplierService.create(actor.getPharmacyTelegramId(), body));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/{supplierId}")
    public ResponseEntity<?> update(
            @PathVariable Long supplierId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SUPPLIER_EDIT);
            return ResponseEntity.ok(pharmacySupplierService.update(actor.getPharmacyTelegramId(), supplierId, body));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{supplierId}/disable")
    public ResponseEntity<?> disable(
            @PathVariable Long supplierId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            PharmacyActor actor = actor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.SUPPLIER_EDIT);
            return ResponseEntity.ok(pharmacySupplierService.disable(actor.getPharmacyTelegramId(), supplierId));
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
