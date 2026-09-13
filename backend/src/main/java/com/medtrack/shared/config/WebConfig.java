package com.medtrack.shared.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final List<String> allowedOrigins;

    public WebConfig(@Value("${medtrack.cors.allowed-origins:http://localhost:5173,http://localhost,http://127.0.0.1:5173,http://127.0.0.1}") String rawOrigins) {
        this.allowedOrigins = parseAndValidateOrigins(rawOrigins);
    }

    public static List<String> parseAndValidateOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("CORS configuration error: medtrack.cors.allowed-origins must not be empty");
        }
        List<String> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (parsed.isEmpty()) {
            throw new IllegalStateException("CORS configuration error: medtrack.cors.allowed-origins contains no valid origins");
        }

        for (String origin : parsed) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "Insecure CORS configuration: Wildcard origin '" + origin + "' is strictly forbidden when credentials are enabled."
                );
            }
        }
        return parsed;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Set-Cookie", "X-Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Idempotency-Key", "Accept", "Origin", "X-Requested-With")
                .exposedHeaders("Set-Cookie", "X-Idempotency-Key")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

