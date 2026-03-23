package com.tenahub.bot.config;

import com.tenahub.bot.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationCleanupRunner implements CommandLineRunner {

    private final RegistrationService registrationService;

    @Override
    public void run(String... args) {
        int deleted = registrationService.deleteInvalidPendingRegistrations();
        System.out.println("Invalid pending registrations deleted: " + deleted);
    }
}