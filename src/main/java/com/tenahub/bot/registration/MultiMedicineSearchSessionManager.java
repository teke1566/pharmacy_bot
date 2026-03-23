package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiMedicineSearchSessionManager {

    private static final Map<Long, MultiMedicineSearchSession> sessions = new ConcurrentHashMap<>();

    public static void start(Long chatId) {
        MultiMedicineSearchSession session = new MultiMedicineSearchSession();
        session.setChatId(chatId);
        session.setWaitingForLocationChoice(true);
        session.setWaitingForMedicineInput(false);
        session.setWaitingForExactLocation(false);
        sessions.put(chatId, session);
    }

    public static boolean exists(Long chatId) {
        return sessions.containsKey(chatId);
    }

    public static MultiMedicineSearchSession get(Long chatId) {
        return sessions.get(chatId);
    }

    public static void remove(Long chatId) {
        sessions.remove(chatId);
    }
}