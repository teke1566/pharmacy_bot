package com.tenahub.bot.service;

public interface SmsService {

    void sendVerificationSms(String phoneNumber, String code);
}