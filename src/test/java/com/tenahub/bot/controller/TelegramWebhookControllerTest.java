package com.tenahub.bot.controller;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.handler.TelegramUpdateRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTest {

    @Mock
    private TelegramUpdateRouter telegramUpdateRouter;

    @Test
    void receiveUpdate_routesToTelegramUpdateRouterWhenSecretUnset() {
        TelegramWebhookController controller = new TelegramWebhookController(telegramUpdateRouter);
        TelegramUpdateDTO update = new TelegramUpdateDTO();

        ResponseEntity<Void> response = controller.receiveUpdate(null, update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(telegramUpdateRouter).route(update);
    }

    @Test
    void receiveUpdate_rejectsMissingSecretWhenConfigured() {
        TelegramWebhookController controller = new TelegramWebhookController(telegramUpdateRouter);
        ReflectionTestUtils.setField(controller, "webhookSecretToken", "expected-secret");
        TelegramUpdateDTO update = new TelegramUpdateDTO();

        ResponseEntity<Void> response = controller.receiveUpdate(null, update);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(telegramUpdateRouter, never()).route(update);
    }

    @Test
    void receiveUpdate_acceptsMatchingSecret() {
        TelegramWebhookController controller = new TelegramWebhookController(telegramUpdateRouter);
        ReflectionTestUtils.setField(controller, "webhookSecretToken", "expected-secret");
        TelegramUpdateDTO update = new TelegramUpdateDTO();

        ResponseEntity<Void> response = controller.receiveUpdate("expected-secret", update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(telegramUpdateRouter).route(update);
    }
}
