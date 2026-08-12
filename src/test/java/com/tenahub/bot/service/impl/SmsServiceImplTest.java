package com.tenahub.bot.service.impl;

import com.tenahub.bot.config.TwilioConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {

    @Mock
    private TwilioConfig twilioConfig;

    @InjectMocks
    private SmsServiceImpl service;

    @Test
    void sendVerificationSms_throwsWhenTwilioIsNotInitialized() {
        when(twilioConfig.getPhoneNumber()).thenReturn("+15550001111");

        assertThrows(Exception.class, () -> service.sendVerificationSms("+251911000000", "123456"));
    }
}
