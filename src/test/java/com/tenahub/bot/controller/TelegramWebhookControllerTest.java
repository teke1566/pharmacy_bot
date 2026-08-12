package com.tenahub.bot.controller;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.handler.TelegramUpdateRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTest {

    @Mock
    private TelegramUpdateRouter telegramUpdateRouter;

    @Test
    void receiveUpdate_routesToTelegramUpdateRouter() {
        TelegramWebhookController controller = new TelegramWebhookController(telegramUpdateRouter);
        TelegramUpdateDTO update = new TelegramUpdateDTO();

        controller.receiveUpdate(update);

        verify(telegramUpdateRouter).route(update);
    }
}
