package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPharmacyManagementSession {
    private String mode;
    private String query;

    public boolean isAwaitingQuery() {
        return mode != null
                && mode.startsWith("SEARCH_")
                && (query == null || query.isBlank());
    }
}