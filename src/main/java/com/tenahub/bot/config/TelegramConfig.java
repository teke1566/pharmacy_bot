package com.tenahub.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {

    private String botToken;

    private String botUsername;

    private String apiUrl;
}