package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateSessionManager {

    private static final Map<Long, UpdateSession> sessions = new ConcurrentHashMap<>();

    public static void start(Long chatId, UpdateField field) {
        sessions.put(chatId, new UpdateSession(field, null, null, null));
    }

    public static boolean exists(Long chatId) {
        return sessions.containsKey(chatId);
    }

    public static UpdateSession get(Long chatId) {
        return sessions.get(chatId);
    }

    public static void remove(Long chatId) {
        sessions.remove(chatId);
    }
}