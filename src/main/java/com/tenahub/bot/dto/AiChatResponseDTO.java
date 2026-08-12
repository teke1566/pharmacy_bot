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
public class AiChatResponseDTO {
    private String answer;
    private String intent;
    private String role;
    private List<String> actionSuggestions;
    // medicine-specific fields (nullable — only populated for medicine intents)
    private String medicineName;
    private String safetyLevel;
    private String use;
    private String howToTake;
    private String sideEffects;
    private String warnings;
}
