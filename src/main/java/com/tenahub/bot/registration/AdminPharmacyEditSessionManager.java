package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminPharmacyEditSessionManager {

    private static final Map<Long, AdminPharmacyEditSession> SESSIONS = new ConcurrentHashMap<>();

    private AdminPharmacyEditSessionManager() {
    }

    public static void save(Long adminChatId, AdminPharmacyEditSession session) {
        SESSIONS.put(adminChatId, session);
    }

    public static boolean exists(Long adminChatId) {
        return SESSIONS.containsKey(adminChatId);
    }

    public static AdminPharmacyEditSession get(Long adminChatId) {
        return SESSIONS.get(adminChatId);
    }

    public static void remove(Long adminChatId) {
        SESSIONS.remove(adminChatId);
    }
}