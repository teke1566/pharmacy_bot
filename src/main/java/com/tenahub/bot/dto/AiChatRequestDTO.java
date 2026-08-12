package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequestDTO {
    private String message;

    // Optional explicit IDs for role/user context.
    private Long telegramUserId;
    private Long pharmacyTelegramId;
    private Long adminTelegramId;

    // Optional reservation targeting for user intents.
    private Long reservationId;
    private String reservationGroupId;
    private String telegramInitData;
    private String initData;
}
