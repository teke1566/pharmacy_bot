package com.tenahub.bot.controller;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.handler.TelegramUpdateRouter;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final TelegramUpdateRouter telegramUpdateRouter;

    @Value("${telegram.webhook.secret-token:}")
    private String webhookSecretToken;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody TelegramUpdateDTO update) {
        if (!isAuthorized(secretToken)) {
            log.warn("Rejected Telegram webhook: missing or invalid secret token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        telegramUpdateRouter.route(update);
        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String secretToken) {
        if (webhookSecretToken == null || webhookSecretToken.isBlank()) {
            return true;
        }
        if (secretToken == null || secretToken.isBlank()) {
            return false;
        }
        byte[] expected = webhookSecretToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = secretToken.getBytes(StandardCharsets.UTF_8);
        return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
    }
}
