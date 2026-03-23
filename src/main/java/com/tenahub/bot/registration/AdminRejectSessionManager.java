package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminRejectSessionManager {

    private static final Map<Long, AdminRejectSession> sessions = new ConcurrentHashMap<>();

    public static void start(Long adminChatId, AdminRejectType type, Long targetId) {
        sessions.put(adminChatId, new AdminRejectSession(type, targetId));
    }

    public static boolean exists(Long adminChatId) {
        return sessions.containsKey(adminChatId);
    }

    public static AdminRejectSession get(Long adminChatId) {
        return sessions.get(adminChatId);
    }

    public static void remove(Long adminChatId) {
        sessions.remove(adminChatId);
    }
}