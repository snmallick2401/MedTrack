package com.medtrack.shared.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsSecurityTest {

    @RestController
    @RequestMapping("/api/v1/test")
    static class DummyApiController {
        @PostMapping("/endpoint")
        public String endpoint() {
            return "ok";
        }
    }

    @Test
    @DisplayName("CORS allows explicitly configured trusted origin with credentials")
    void allowsTrustedOriginWithCredentials() {
        WebConfig webConfig = new WebConfig("http://localhost:5173, http://localhost");
        CorsConfigurationSource source = webConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/refresh");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config, "CORS configuration must exist for /api/**");
        assertTrue(config.getAllowCredentials(), "Credentials must be enabled for trusted origins");
        assertEquals(List.of("http://localhost:5173", "http://localhost"), config.getAllowedOrigins());

        assertEquals("http://localhost:5173", config.checkOrigin("http://localhost:5173"));
        assertEquals("http://localhost", config.checkOrigin("http://localhost"));
    }

    @Test
    @DisplayName("CORS strictly rejects untrusted origin and does not return allow header")
    void rejectsUntrustedOrigin() {
        WebConfig webConfig = new WebConfig("http://localhost:5173, http://localhost");
        CorsConfigurationSource source = webConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/refresh");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        assertNull(config.checkOrigin("https://attacker.com"), "Untrusted origin must be rejected");
        assertNull(config.checkOrigin("https://evil-pharma.org"), "Untrusted origin must be rejected");
        assertNull(config.checkOrigin("http://localhost:3000"), "Unconfigured port must be rejected");
    }

    @Test
    @DisplayName("Configuring wildcard origins with credentials must fail closed on startup")
    void rejectsWildcardWithCredentialsOnStartup() {
        IllegalStateException ex1 = assertThrows(
            IllegalStateException.class,
            () -> new WebConfig("http://localhost:5173, https://*")
        );
        assertTrue(ex1.getMessage().contains("Wildcard origin 'https://*' is strictly forbidden"));

        IllegalStateException ex2 = assertThrows(
            IllegalStateException.class,
            () -> new WebConfig("*")
        );
        assertTrue(ex2.getMessage().contains("Wildcard origin '*' is strictly forbidden"));
    }

    @Test
    @DisplayName("Configuring empty origins must fail closed on startup")
    void rejectsBlankOrEmptyOrigins() {
        assertThrows(IllegalStateException.class, () -> new WebConfig(""));
        assertThrows(IllegalStateException.class, () -> new WebConfig("   ,   "));
        assertThrows(IllegalStateException.class, () -> new WebConfig(null));
    }

    @Test
    @DisplayName("Pre-flight OPTIONS request from trusted origin succeeds with expected headers")
    void preflightSucceedsForTrustedOrigin() throws Exception {
        WebConfig webConfig = new WebConfig("http://localhost:5173, http://localhost");

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyApiController())
                .addFilters(new org.springframework.web.filter.CorsFilter(webConfig.corsConfigurationSource()))
                .build();

        mockMvc.perform(options("/api/v1/test/endpoint")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type, X-Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    @DisplayName("Pre-flight OPTIONS request from untrusted origin is rejected without allow header")
    void preflightFailsForUntrustedOrigin() throws Exception {
        WebConfig webConfig = new WebConfig("http://localhost:5173, http://localhost");

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyApiController())
                .addFilters(new org.springframework.web.filter.CorsFilter(webConfig.corsConfigurationSource()))
                .build();

        mockMvc.perform(options("/api/v1/test/endpoint")
                .header("Origin", "https://attacker.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
