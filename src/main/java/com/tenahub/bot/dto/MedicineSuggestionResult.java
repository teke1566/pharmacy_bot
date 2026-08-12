package com.tenahub.bot.dto;

import java.util.List;

public record MedicineSuggestionResult(
        String canonicalInput,
        List<String> typoSuggestions,
        List<String> alternativeSuggestions
) {
    public boolean hasSuggestions() {
        return (typoSuggestions != null && !typoSuggestions.isEmpty())
                || (alternativeSuggestions != null && !alternativeSuggestions.isEmpty());
    }
}