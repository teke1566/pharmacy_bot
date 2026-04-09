package com.tenahub.bot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MiniAppCorsConfig implements WebMvcConfigurer {

    private static final String[] MINI_APP_ALLOWED_ORIGINS = {
        "https://tenahub-miniapp.vercel.app",
        "http://localhost:5174"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/miniapp/**")
        .allowedOrigins(MINI_APP_ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/proxyapi/api/miniapp/**")
        .allowedOrigins(MINI_APP_ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/api/pharmacy/**")
            .allowedOrigins(MINI_APP_ALLOWED_ORIGINS)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);

        registry.addMapping("/proxyapi/api/pharmacy/**")
            .allowedOrigins(MINI_APP_ALLOWED_ORIGINS)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
