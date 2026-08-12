package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiReservationSessionManager {

    private static final Map<Long, MultiReservationSession> SESSIONS = new ConcurrentHashMap<>();

    public static void start(Long chatId, Long pharmacyId, java.util.List<String> matchedMedicines) {
        MultiReservationSession session = new MultiReservationSession();
        session.setPharmacyId(pharmacyId);
        session.setMatchedMedicines(matchedMedicines);
        session.setCurrentStep("PICKING_QUANTITIES");
        session.setWaitingForName(false);
        session.setWaitingForPhone(false);
        SESSIONS.put(chatId, session);
    }

    public static boolean exists(Long chatId) {
        return SESSIONS.containsKey(chatId);
    }

    public static MultiReservationSession get(Long chatId) {
        return SESSIONS.get(chatId);
    }

    public static void remove(Long chatId) {
        SESSIONS.remove(chatId);
    }
}
