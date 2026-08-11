package com.tenahub.bot.handler;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Entry point for Telegram webhook updates. Delegates to focused message/callback handlers.
 */
@Service
@RequiredArgsConstructor
public class TelegramWebhookFacade {

    private final TelegramCallbackHandler telegramCallbackHandler;
    private final TelegramMessageHandler telegramMessageHandler;

    public void handleCallback(TelegramUpdateDTO update) {
        telegramCallbackHandler.handleCallback(update);
    }

    public void handleMessage(TelegramUpdateDTO update) {
        telegramMessageHandler.handleMessage(update);
    }
}
