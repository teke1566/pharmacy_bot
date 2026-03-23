package com.tenahub.bot.registration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegistrationSessionManager {

    private static final Map<Long, RegistrationSession> sessions =
            new ConcurrentHashMap<>();

    public static void start(Long chatId){

        RegistrationSession s = new RegistrationSession();

        s.setStep(RegistrationStep.NAME);

        sessions.put(chatId, s);
    }

    public static RegistrationSession get(Long chatId){

        return sessions.get(chatId);
    }

    public static boolean exists(Long chatId){

        return sessions.containsKey(chatId);
    }

    public static void remove(Long chatId){

        sessions.remove(chatId);
    }
}