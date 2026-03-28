package com.tenahub.bot.util;

import java.util.Locale;

public enum BotLanguage {
    ENGLISH("en"),
    AMHARIC("am");

    private final String code;

    BotLanguage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static BotLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ENGLISH;
        }

        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(AMHARIC.code)) {
            return AMHARIC;
        }

        return ENGLISH;
    }
}