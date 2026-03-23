package com.tenahub.bot.controller;

import com.tenahub.bot.dto.TelegramUpdateDTO;
import com.tenahub.bot.handler.TelegramUpdateRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final TelegramUpdateRouter telegramUpdateRouter;

    @PostMapping("/webhook")
    public void receiveUpdate(@RequestBody TelegramUpdateDTO update) {
        telegramUpdateRouter.route(update);
    }
}