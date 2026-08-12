package com.tenahub.bot.controller;

import com.tenahub.bot.dto.AiChatDebugResponseDTO;
import com.tenahub.bot.dto.AiChatRequestDTO;
import com.tenahub.bot.dto.AiChatResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.AiAssistantService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/ai", "/proxyapi/api/ai"})
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final TelegramWebAppAuthService telegramWebAppAuthService;

    @Value("${tenahub.ai.debug-enabled:false}")
    private boolean aiDebugEnabled;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody AiChatRequestDTO request,
                                  @RequestHeader(value = "X-User-Telegram-Id", required = false) Long userTelegramId,
                                  @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                                  @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long adminTelegramId,
                                  @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            applyAuthenticatedActor(request, initDataHeader, userTelegramId, pharmacyTelegramId, adminTelegramId);
            AiChatResponseDTO response = aiAssistantService.chat(
                    request,
                    request.getTelegramUserId(),
                    request.getPharmacyTelegramId(),
                    request.getAdminTelegramId());
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[AI] chat rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[AI] chat unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    @PostMapping("/chat/debug")
    public ResponseEntity<?> chatDebug(@RequestBody AiChatRequestDTO request,
                                       @RequestHeader(value = "X-User-Telegram-Id", required = false) Long userTelegramId,
                                       @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                                       @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long adminTelegramId,
                                       @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        if (!aiDebugEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("AI debug endpoint is disabled").build());
        }

        try {
            applyAuthenticatedActor(request, initDataHeader, userTelegramId, pharmacyTelegramId, adminTelegramId);
            AiChatDebugResponseDTO response = aiAssistantService.chatDebug(
                    request,
                    request.getTelegramUserId(),
                    request.getPharmacyTelegramId(),
                    request.getAdminTelegramId());
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[AI] chat debug rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[AI] chat debug unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    private void applyAuthenticatedActor(AiChatRequestDTO request,
                                         String initDataHeader,
                                         Long userTelegramId,
                                         Long pharmacyTelegramId,
                                         Long adminTelegramId) {
        if (request == null) {
            throw new MiniAppAuthException("request body is required");
        }
        Long actorId = telegramWebAppAuthService.requireUserId(
                initDataHeader, request.getTelegramInitData(), request.getInitData());
        request.setTelegramUserId(actorId);

        Long claimedPharmacyId = firstPositive(pharmacyTelegramId, request.getPharmacyTelegramId());
        request.setPharmacyTelegramId(actorId.equals(claimedPharmacyId) ? actorId : null);

        Long claimedAdminId = firstPositive(adminTelegramId, request.getAdminTelegramId(), userTelegramId);
        if (adminChatId > 0 && actorId == adminChatId && (claimedAdminId == null || claimedAdminId.equals(actorId))) {
            request.setAdminTelegramId(actorId);
        } else {
            request.setAdminTelegramId(null);
        }
    }

    private Long firstPositive(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }
}
