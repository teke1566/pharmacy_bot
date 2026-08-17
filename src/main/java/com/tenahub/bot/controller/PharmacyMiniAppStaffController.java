package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PharmacyStaffInviteRequestDTO;
import com.tenahub.bot.dto.PharmacyStaffUpdateRequestDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/staff", "/proxyapi/api/pharmacy/staff"})
public class PharmacyMiniAppStaffController {

    private final PharmacyStaffService pharmacyStaffService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.list(actor(headerPharmacyId, paramPharmacyId)));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.metrics(actor(headerPharmacyId, paramPharmacyId)));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/roles")
    public ResponseEntity<?> roles() {
        return ResponseEntity.ok(pharmacyStaffService.rolesCatalog());
    }

    @PostMapping("/invite")
    public ResponseEntity<?> invite(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody PharmacyStaffInviteRequestDTO body) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.invite(actor(headerPharmacyId, paramPharmacyId), body));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/invites/{token}/accept")
    public ResponseEntity<?> acceptInvite(@PathVariable String token) {
        try {
            Long actorTelegramId = miniAppActorResolver.requireUserId();
            return ResponseEntity.ok(pharmacyStaffService.acceptInvite(token, actorTelegramId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{staffId}")
    public ResponseEntity<?> get(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.get(actor(headerPharmacyId, paramPharmacyId), staffId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PatchMapping("/{staffId}")
    public ResponseEntity<?> update(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody PharmacyStaffUpdateRequestDTO body) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.update(actor(headerPharmacyId, paramPharmacyId), staffId, body));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/{staffId}/permissions")
    public ResponseEntity<?> permissions(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, List<String>> body) {
        try {
            List<String> grants = body == null ? List.of() : body.getOrDefault("grantPermissions", List.of());
            List<String> denials = body == null ? List.of() : body.getOrDefault("denyPermissions", List.of());
            return ResponseEntity.ok(pharmacyStaffService.replacePermissions(
                    actor(headerPharmacyId, paramPharmacyId), staffId, grants, denials));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{staffId}/suspend")
    public ResponseEntity<?> suspend(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body == null ? null : body.get("reason");
            return ResponseEntity.ok(pharmacyStaffService.suspend(actor(headerPharmacyId, paramPharmacyId), staffId, reason));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{staffId}/activate")
    public ResponseEntity<?> activate(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.activate(actor(headerPharmacyId, paramPharmacyId), staffId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{staffId}/disable")
    public ResponseEntity<?> disable(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body == null ? null : body.get("reason");
            return ResponseEntity.ok(pharmacyStaffService.disable(actor(headerPharmacyId, paramPharmacyId), staffId, reason));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{staffId}/activity")
    public ResponseEntity<?> activity(
            @PathVariable Long staffId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        try {
            return ResponseEntity.ok(pharmacyStaffService.activity(actor(headerPharmacyId, paramPharmacyId), staffId, from, to));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private PharmacyActor actor(Long headerPharmacyId, Long paramPharmacyId) {
        return miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("does not belong") || message.contains("missing permission")) {
            status = HttpStatus.FORBIDDEN;
        } else if (message.contains("not found")) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
