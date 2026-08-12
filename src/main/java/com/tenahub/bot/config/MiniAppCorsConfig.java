package com.tenahub.bot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MiniAppCorsConfig implements WebMvcConfigurer {

    static final String[] MINI_APP_ALLOWED_ORIGINS = {
        "https://tenahub-miniapp.vercel.app",
        "https://tenahub-miniapp-2j4soxvr8-teke-alone.vercel.app",
        "http://localhost:5174"
    };

    static final String[] MINI_APP_API_PATHS = {
        "/api/miniapp/**",
        "/proxyapi/api/miniapp/**",
        "/api/pharmacy/**",
        "/proxyapi/api/pharmacy/**",
        "/api/admin/**",
        "/proxyapi/api/admin/**",
        "/api/ai/**",
        "/proxyapi/api/ai/**"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        for (String path : MINI_APP_API_PATHS) {
            registry.addMapping(path)
                    .allowedOrigins(MINI_APP_ALLOWED_ORIGINS)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
