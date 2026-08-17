package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class MiniAppActorResolver {

    public static final String INIT_DATA_HEADER = "X-Telegram-Init-Data";

    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyStaffAccessService pharmacyStaffAccessService;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    public MiniAppActorResolver(TelegramWebAppAuthService telegramWebAppAuthService,
                                PharmacyRepository pharmacyRepository,
                                @Lazy PharmacyStaffAccessService pharmacyStaffAccessService) {
        this.telegramWebAppAuthService = telegramWebAppAuthService;
        this.pharmacyRepository = pharmacyRepository;
        this.pharmacyStaffAccessService = pharmacyStaffAccessService;
    }

    /**
     * Returns the pharmacy owner telegram id used for data scoping.
     * Supports owner (actor == claim) and ACTIVE staff of that pharmacy.
     */
    public Long requirePharmacyTelegramId(Long headerValue, Long paramValue) {
        return requirePharmacyActor(headerValue, paramValue).getPharmacyTelegramId();
    }

    public PharmacyActor requirePharmacyActor(Long headerValue, Long paramValue) {
        Long actorTelegramId = telegramWebAppAuthService.requireUserId(currentInitData());
        Long claimedPharmacyTelegramId = firstPositive(headerValue, paramValue);

        Long pharmacyTelegramId = claimedPharmacyTelegramId != null ? claimedPharmacyTelegramId : actorTelegramId;
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new MiniAppAuthException("Pharmacy not found"));

        return pharmacyStaffAccessService.resolveActor(pharmacy, actorTelegramId);
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
        Long fromInitData = telegramWebAppAuthService.requireUserId(currentInitData());
        Long claimed = firstPositive(headerValue, paramValue);
        if (claimed != null && !claimed.equals(fromInitData)) {
            throw new MiniAppAuthException("Telegram identity does not match adminTelegramId");
        }
        return fromInitData;
    }

    public Long requireUserId() {
        return telegramWebAppAuthService.requireUserId(currentInitData());
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
