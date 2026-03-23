package com.tenahub.bot.handler;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramUpdateRouter {

    private final TelegramWebhookFacade telegramWebhookFacade;

    public void route(TelegramUpdateDTO update) {
        if (update == null) {
            return;
        }

        if (update.getCallbackQuery() != null) {
            telegramWebhookFacade.handleCallback(update);
            return;
        }

        if (update.getMessage() != null) {
            telegramWebhookFacade.handleMessage(update);
        }
    }
}