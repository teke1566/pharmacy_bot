package com.tenahub.bot.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class MiniAppActorResolver {

    public static final String INIT_DATA_HEADER = "X-Telegram-Init-Data";

    private final TelegramWebAppAuthService telegramWebAppAuthService;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    public MiniAppActorResolver(TelegramWebAppAuthService telegramWebAppAuthService) {
        this.telegramWebAppAuthService = telegramWebAppAuthService;
    }

    public Long requirePharmacyTelegramId(Long headerValue, Long paramValue) {
        Long claimed = firstPositive(headerValue, paramValue);
        Long fromInitData = telegramWebAppAuthService.requireUserId(currentInitData());
        if (claimed != null && !claimed.equals(fromInitData)) {
            throw new MiniAppAuthException("Telegram identity does not match pharmacyTelegramId");
        }
        return fromInitData;
    }

    public Long requireAdminTelegramId(Long headerValue, Long paramValue) {
        Long claimed = firstPositive(headerValue, paramValue);
        Long fromInitData = telegramWebAppAuthService.requireUserId(currentInitData());
        if (claimed != null && !claimed.equals(fromInitData)) {
            throw new MiniAppAuthException("Telegram identity does not match adminTelegramId");
        }
        if (adminChatId <= 0 || fromInitData != adminChatId) {
            throw new MiniAppAuthException("Access denied — admin only");
        }
        return fromInitData;
    }

    public Long resolveAdminIdForAccessCheck(Long headerValue, Long paramValue) {
        // Always require verified Telegram initData — never trust spoofable header/param alone.
        Long fromInitData = telegramWebAppAuthService.requireUserId(currentInitData());
        Long claimed = firstPositive(headerValue, paramValue);
        if (claimed != null && !claimed.equals(fromInitData)) {
            throw new MiniAppAuthException("Telegram identity does not match adminTelegramId");
        }
        return fromInitData;
    }

    public String currentInitData() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            authorization = request.getHeader("authorization");
        }
        String fromAuthorization = extractTmaInitData(authorization);
        if (fromAuthorization != null) {
            return fromAuthorization;
        }

        String header = request.getHeader(INIT_DATA_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        // Some clients / proxies only forward lowercase custom headers.
        String lowerHeader = request.getHeader("x-telegram-init-data");
        if (lowerHeader != null && !lowerHeader.isBlank()) {
            return lowerHeader;
        }
        String telegramInitData = request.getParameter("telegramInitData");
        if (telegramInitData != null && !telegramInitData.isBlank()) {
            return telegramInitData;
        }
        String initData = request.getParameter("initData");
        if (initData != null && !initData.isBlank()) {
            return initData;
        }
        return null;
    }

    private String extractTmaInitData(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.length() < 5) {
            return null;
        }
        if (!trimmed.regionMatches(true, 0, "tma ", 0, 4)) {
            return null;
        }
        String initData = trimmed.substring(4).trim();
        return initData.isBlank() ? null : initData;
    }

    private Long firstPositive(Long first, Long second) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return null;
    }
}
