package com.tenahub.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class MiniAppCorsConfig implements WebMvcConfigurer {

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

    @Value("${tenahub.mini-app.allowed-origins:https://tenahub-miniapp.vercel.app}")
    private String allowedOriginsCsv;

    static List<String> parseOrigins(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of("https://tenahub-miniapp.vercel.app");
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    List<String> allowedOrigins() {
        return parseOrigins(allowedOriginsCsv);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins().toArray(String[]::new);
        for (String path : MINI_APP_API_PATHS) {
            registry.addMapping(path)
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
