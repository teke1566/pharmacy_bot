package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MedicineSearchSessionManager {

    private static final Map<Long, MedicineSearchSession> SESSIONS = new ConcurrentHashMap<>();

    public static void save(Long chatId, String medicineName, SearchFilterType filter) {
        SESSIONS.put(chatId, new MedicineSearchSession(medicineName, filter));
    }

    public static boolean exists(Long chatId) {
        return SESSIONS.containsKey(chatId);
    }

    public static MedicineSearchSession get(Long chatId) {
        return SESSIONS.get(chatId);
    }

    public static void remove(Long chatId) {
        SESSIONS.remove(chatId);
    }
}