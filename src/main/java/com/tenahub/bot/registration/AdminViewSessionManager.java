package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminViewSessionManager {

    private static final Map<Long, AdminViewSession> SESSIONS = new ConcurrentHashMap<>();

    public static void save(Long adminChatId, AdminViewSession session) {
        SESSIONS.put(adminChatId, session);
    }

    public static boolean exists(Long adminChatId) {
        return SESSIONS.containsKey(adminChatId);
    }

    public static AdminViewSession get(Long adminChatId) {
        return SESSIONS.get(adminChatId);
    }

    public static void remove(Long adminChatId) {
        SESSIONS.remove(adminChatId);
    }
}