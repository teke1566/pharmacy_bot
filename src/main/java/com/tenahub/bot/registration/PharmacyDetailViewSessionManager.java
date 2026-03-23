package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PharmacyDetailViewSessionManager {

    private static final Map<Long, PharmacyDetailViewSession> SESSIONS = new ConcurrentHashMap<>();

    public static void save(Long chatId, PharmacyDetailViewSession session) {
        SESSIONS.put(chatId, session);
    }

    public static PharmacyDetailViewSession get(Long chatId) {
        return SESSIONS.get(chatId);
    }

    public static boolean exists(Long chatId) {
        return SESSIONS.containsKey(chatId);
    }

    public static void remove(Long chatId) {
        SESSIONS.remove(chatId);
    }
}