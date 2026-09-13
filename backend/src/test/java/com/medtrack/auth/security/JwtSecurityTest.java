package com.medtrack.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.entity.Permission;
import com.medtrack.auth.entity.Role;
import com.medtrack.user.entity.User;
import com.medtrack.user.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtSecurityTest {

    private static final String VALID_TEST_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef_TEST_KEY_512_BITS_LONG";
    private static final String COMPROMISED_DEFAULT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String ANOTHER_VALID_SECRET =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210_ANOTHER_KEY_512_BITS";

    private User createMockUser(String email, String roleName) {
        User user = org.mockito.Mockito.mock(User.class);
        Role role = org.mockito.Mockito.mock(Role.class);
        Permission p1 = org.mockito.Mockito.mock(Permission.class);
        Permission p2 = org.mockito.Mockito.mock(Permission.class);

        org.mockito.Mockito.when(p1.getName()).thenReturn("MEDICINE_READ");
        org.mockito.Mockito.when(p2.getName()).thenReturn("MEDICINE_CREATE");
        org.mockito.Mockito.when(role.getName()).thenReturn(roleName);
        org.mockito.Mockito.when(role.getPermissions()).thenReturn(Set.of(p1, p2));

        org.mockito.Mockito.when(user.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        org.mockito.Mockito.when(user.getEmail()).thenReturn(email);
        org.mockito.Mockito.when(user.getRole()).thenReturn(role);
        org.mockito.Mockito.when(user.getAssignedWarehouse()).thenReturn(null);
        org.mockito.Mockito.when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        return user;
    }

    @Test
    @DisplayName("Test 1: Valid 64+ byte secret creates HS512 SecretKey successfully")
    void validSecretBuildsKeySuccessfully() {
        SecretKey key = JwtService.validateAndBuildKey(VALID_TEST_SECRET);
        assertNotNull(key);
        assertEquals("HmacSHA512", key.getAlgorithm());
    }

    @Test
    @DisplayName("Test 2: Missing (null) secret throws IllegalStateException (Fail Closed)")
    void missingSecretThrowsException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtService.validateAndBuildKey(null)
        );
        assertTrue(ex.getMessage().contains("JWT signing key is missing"));
    }

    @Test
    @DisplayName("Test 3: Blank secret throws IllegalStateException (Fail Closed)")
    void blankSecretThrowsException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtService.validateAndBuildKey("   \t\n  ")
        );
        assertTrue(ex.getMessage().contains("JWT signing key is missing"));
    }

    @Test
    @DisplayName("Test 4: Weak secret (< 64 bytes for HS512) throws IllegalStateException")
    void weakSecretThrowsException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtService.validateAndBuildKey("short-secret-less-than-64-bytes-1234567890")
        );
        assertTrue(ex.getMessage().contains("Secret must be at least 64 bytes (512 bits) for HMAC-SHA512"));
    }

    @Test
    @DisplayName("Test 5: Known compromised/default secret is strictly rejected at startup")
    void compromisedDefaultSecretIsRejected() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtService.validateAndBuildKey(COMPROMISED_DEFAULT_SECRET)
        );
        assertTrue(ex.getMessage().contains("Default or compromised secret detected"));
    }

    @Test
    @DisplayName("Test 6: Common placeholder secrets ('change-me', 'secret', etc.) are rejected")
    void commonPlaceholdersAreRejected() {
        assertThrows(IllegalStateException.class, () -> JwtService.validateAndBuildKey("change-me"));
        assertThrows(IllegalStateException.class, () -> JwtService.validateAndBuildKey("changeme"));
        assertThrows(IllegalStateException.class, () -> JwtService.validateAndBuildKey("secret"));
        assertThrows(IllegalStateException.class, () -> JwtService.validateAndBuildKey("password"));
    }

    @Test
    @DisplayName("Test 7: JWT is successfully issued with expected claims and expiration")
    void issuesValidJwt() {
        JwtProperties props = new JwtProperties(VALID_TEST_SECRET, 15, 7);
        JwtService service = new JwtService(props);
        User user = createMockUser("admin@medtrack.local", "SUPER_ADMIN");

        String token = service.issue(user);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Claims claims = service.claims(token);
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals("admin@medtrack.local", claims.get("email"));
        assertEquals("SUPER_ADMIN", claims.get("role"));
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    @DisplayName("Test 8: Token signed with a different key is rejected with SignatureException")
    void rejectsTokenSignedWithWrongKey() {
        JwtProperties props1 = new JwtProperties(VALID_TEST_SECRET, 15, 7);
        JwtProperties props2 = new JwtProperties(ANOTHER_VALID_SECRET, 15, 7);

        JwtService service1 = new JwtService(props1);
        JwtService service2 = new JwtService(props2);

        User user = createMockUser("attacker@medtrack.local", "STORE_MANAGER");
        String tokenFromService1 = service1.issue(user);

        assertThrows(SignatureException.class, () -> service2.claims(tokenFromService1));
    }

    @Test
    @DisplayName("Test 9: Token signed with unexpected algorithm (e.g. HS256 downgrade) is rejected")
    void rejectsTokenWithWrongAlgorithm() {
        // Create an HS256 key (32 bytes)
        byte[] hs256KeyBytes = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        SecretKey hs256Key = Keys.hmacShaKeyFor(hs256KeyBytes);

        String downgradedToken = Jwts.builder()
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("email", "admin@medtrack.local")
                .claim("role", "SUPER_ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(hs256Key, Jwts.SIG.HS256)
                .compact();

        JwtProperties props = new JwtProperties(VALID_TEST_SECRET, 15, 7);
        JwtService service = new JwtService(props);

        assertThrows(JwtException.class, () -> service.claims(downgradedToken));
    }

    @Test
    @DisplayName("Test 10: Expired token is rejected with ExpiredJwtException")
    void rejectsExpiredToken() {
        SecretKey key = JwtService.validateAndBuildKey(VALID_TEST_SECRET);
        Instant past = Instant.now().minusSeconds(3600);

        String expiredToken = Jwts.builder()
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("email", "user@medtrack.local")
                .claim("role", "STORE_MANAGER")
                .issuedAt(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        JwtProperties props = new JwtProperties(VALID_TEST_SECRET, 15, 7);
        JwtService service = new JwtService(props);

        assertThrows(ExpiredJwtException.class, () -> service.claims(expiredToken));
    }

    @Test
    @DisplayName("Test 11: Startup validation exceptions never expose secret material in message")
    void startupExceptionsDoNotLeakSecret() {
        String sensitiveSecret = "sensitive_leaked_too_short_secret_value";
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtService.validateAndBuildKey(sensitiveSecret)
        );
        assertFalse(ex.getMessage().contains(sensitiveSecret), "Exception message must never contain the raw secret");
    }

    @Test
    @DisplayName("Test 12: Attack Replay - Forged token signed with old exposed default key is rejected")
    void forgedTokenUsingOldCompromisedDefaultIsRejected() {
        byte[] oldCompromisedBytes = COMPROMISED_DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey oldKey = Keys.hmacShaKeyFor(oldCompromisedBytes);

        // Attacker creates forged superadmin token using old known key
        String forgedToken = Jwts.builder()
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("email", "admin@medtrack.local")
                .claim("role", "SUPER_ADMIN")
                .claim("permissions", List.of("USER_READ", "USER_CREATE", "AUDIT_READ", "MEDICINE_CREATE"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(oldKey)
                .compact();

        // Production service configured with new valid secret
        JwtProperties prodProps = new JwtProperties(VALID_TEST_SECRET, 15, 7);
        JwtService prodService = new JwtService(prodProps);

        assertThrows(SignatureException.class, () -> prodService.claims(forgedToken));
    }
}
