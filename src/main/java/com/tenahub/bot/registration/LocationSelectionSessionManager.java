package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocationSelectionSessionManager {

    private static final Map<Long, LocationSelectionSession> SESSIONS = new ConcurrentHashMap<>();

    public static void start(Long chatId, LocationFlowType flowType) {
        LocationSelectionSession session = new LocationSelectionSession();
        session.setChatId(chatId);
        session.setFlowType(flowType);
        session.setRegionMode();
        SESSIONS.put(chatId, session);
    }

    public static boolean exists(Long chatId) {
        return SESSIONS.containsKey(chatId);
    }

    public static LocationSelectionSession get(Long chatId) {
        return SESSIONS.get(chatId);
    }

    public static void remove(Long chatId) {
        SESSIONS.remove(chatId);
    }
}