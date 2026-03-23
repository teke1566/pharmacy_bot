package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReservationSessionManager {

    private static final Map<Long, ReservationSession> SESSIONS = new ConcurrentHashMap<>();

    public static void start(Long userId, Long pharmacyId, String medicineName) {
        ReservationSession session = new ReservationSession();
        session.setPharmacyId(pharmacyId);
        session.setMedicineName(medicineName);
        session.setQuantity(null);
        session.setCustomerName(null);
        session.setWaitingForCustomQuantity(false);
        session.setWaitingForName(false);
        session.setWaitingForPhone(false);
        session.setSourceMessageId(null); // add this
        SESSIONS.put(userId, session);
    }

    public static boolean exists(Long userId) {
        return SESSIONS.containsKey(userId);
    }

    public static ReservationSession get(Long userId) {
        return SESSIONS.get(userId);
    }

    public static void remove(Long userId) {
        SESSIONS.remove(userId);
    }
}