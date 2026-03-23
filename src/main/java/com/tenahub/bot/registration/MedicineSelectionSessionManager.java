package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MedicineSelectionSessionManager {

    private static final Map<Long, MedicineSelectionSession> sessions = new ConcurrentHashMap<>();

    public static void start(Long chatId, boolean forRegistration) {
        MedicineSelectionSession session = new MedicineSelectionSession();
        session.setForRegistration(forRegistration);
        sessions.put(chatId, session);
    }

    public static boolean exists(Long chatId) {
        return sessions.containsKey(chatId);
    }

    public static MedicineSelectionSession get(Long chatId) {
        return sessions.get(chatId);
    }

    public static void remove(Long chatId) {
        sessions.remove(chatId);
    }
}