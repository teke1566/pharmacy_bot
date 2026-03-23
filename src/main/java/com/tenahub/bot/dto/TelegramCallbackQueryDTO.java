package com.tenahub.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TelegramCallbackQueryDTO {

    @JsonProperty("id")
    private String id;
    private String data;
    private TelegramMessageDTO message;

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public TelegramMessageDTO getMessage() {
        return message;
    }
}