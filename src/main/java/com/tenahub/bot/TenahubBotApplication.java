package com.tenahub.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling

public class TenahubBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenahubBotApplication.class, args);
	}

}
