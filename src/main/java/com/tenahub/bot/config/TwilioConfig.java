package com.tenahub.bot.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

@Configuration
@Getter
public class TwilioConfig {

    private final Environment environment;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String phoneNumber;

    public TwilioConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        if (!StringUtils.hasText(accountSid) || !StringUtils.hasText(authToken)) {
            throw new IllegalStateException("Twilio account-sid and auth-token are required outside the test profile");
        }
        Twilio.init(accountSid, authToken);
    }
}
