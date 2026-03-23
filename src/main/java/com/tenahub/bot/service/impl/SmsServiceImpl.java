package com.tenahub.bot.service.impl;

import com.tenahub.bot.config.TwilioConfig;
import com.tenahub.bot.service.SmsService;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final TwilioConfig twilioConfig;

    @Override
    public void sendVerificationSms(String phoneNumber, String code){

        Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(twilioConfig.getPhoneNumber()),
                "Your TenaHub verification code is: " + code
        ).create();
    }
}