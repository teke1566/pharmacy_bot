package com.tenahub.bot.service;

import java.util.List;

public interface MedicineSearchLogService {
    void logSearch(Long userId, String medicineName);
    List<String> getRecentSearches(Long userId);
}
