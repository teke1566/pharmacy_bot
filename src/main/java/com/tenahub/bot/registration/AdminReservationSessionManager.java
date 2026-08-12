package com.tenahub.bot.registration;

import java.util.concurrent.ConcurrentHashMap;

public class AdminReservationSessionManager {

    private static final ConcurrentHashMap<Long, AdminReservationSession> SESSIONS = new ConcurrentHashMap<>();

    public static void save(Long chatId, AdminReservationSession session) {
        SESSIONS.put(chatId, session);
    }

    public static boolean exists(Long chatId) {
        return SESSIONS.containsKey(chatId);
    }

    public static AdminReservationSession get(Long chatId) {
        return SESSIONS.get(chatId);
    }

    public static void remove(Long chatId) {
        SESSIONS.remove(chatId);
    }
}
