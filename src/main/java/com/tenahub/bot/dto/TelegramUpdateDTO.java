package com.tenahub.bot.dto;

import lombok.Data;

@Data
public class TelegramUpdateDTO {

    private TelegramMessageDTO message;
    private TelegramCallbackQueryDTO callback_query;

    public TelegramMessageDTO getMessage() {
        return message;
    }

    public TelegramCallbackQueryDTO getCallbackQuery() {
        return callback_query;
    }
}