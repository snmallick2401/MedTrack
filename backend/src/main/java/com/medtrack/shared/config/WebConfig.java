package com.medtrack.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${medtrack.cors.allowed-origins:http://localhost:5173,http://localhost,https://*}")
    private String origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] originList = origins.split(",");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(originList)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Idempotency-Key")
                .allowCredentials(true);
    }
}

