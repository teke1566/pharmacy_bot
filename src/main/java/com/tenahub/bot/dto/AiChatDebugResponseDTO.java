package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatDebugResponseDTO {
    private AiChatResponseDTO response;
    private String matchedIntent;
    private String resolvedRole;
    private Long actorTelegramId;
    private List<String> dataSources;
}
