package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PharmacyRejectSessionManager {

    private static final Map<Long, PharmacyRejectSession> sessions = new ConcurrentHashMap<>();

    private PharmacyRejectSessionManager() {
    }

    public static void startReservation(Long pharmacyChatId, Long reservationId) {
        sessions.put(pharmacyChatId, new PharmacyRejectSession(PharmacyRejectType.RESERVATION, reservationId, null));
    }

    public static void startReservationGroup(Long pharmacyChatId, String groupId) {
        sessions.put(pharmacyChatId, new PharmacyRejectSession(PharmacyRejectType.RESERVATION_GROUP, null, groupId));
    }

    public static void startPrescriptionReservation(Long pharmacyChatId, Long reservationId) {
        sessions.put(pharmacyChatId, new PharmacyRejectSession(PharmacyRejectType.PRESCRIPTION_RESERVATION, reservationId, null));
    }

    public static void startPrescriptionGroup(Long pharmacyChatId, String groupId) {
        sessions.put(pharmacyChatId, new PharmacyRejectSession(PharmacyRejectType.PRESCRIPTION_GROUP, null, groupId));
    }

    public static boolean exists(Long pharmacyChatId) {
        return sessions.containsKey(pharmacyChatId);
    }

    public static PharmacyRejectSession get(Long pharmacyChatId) {
        return sessions.get(pharmacyChatId);
    }

    public static void remove(Long pharmacyChatId) {
        sessions.remove(pharmacyChatId);
    }
}
