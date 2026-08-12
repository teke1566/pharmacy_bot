package com.tenahub.bot.service;

public interface TelegramWebAppAuthService {

    Long requireUserId(String... initDataCandidates);

    Long resolveUserId(String initData, Long claimedUserId);

    Long parseUserId(String initData);
}
