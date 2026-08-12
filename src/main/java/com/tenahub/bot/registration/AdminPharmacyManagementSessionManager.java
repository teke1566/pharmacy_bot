package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminPharmacyManagementSessionManager {

    private static final Map<Long, AdminPharmacyManagementSession> SESSIONS = new ConcurrentHashMap<>();

    private AdminPharmacyManagementSessionManager() {
    }

    public static void save(Long adminChatId, AdminPharmacyManagementSession session) {
        SESSIONS.put(adminChatId, session);
    }

    public static boolean exists(Long adminChatId) {
        return SESSIONS.containsKey(adminChatId);
    }

    public static AdminPharmacyManagementSession get(Long adminChatId) {
        return SESSIONS.get(adminChatId);
    }

    public static void remove(Long adminChatId) {
        SESSIONS.remove(adminChatId);
    }
}