package com.medtrack.auth.security;

import com.medtrack.user.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final String EXPECTED_ALGORITHM = "HS512";
    public static final int MIN_KEY_BYTES = 64; // 512 bits required for HS512
    private static final String KNOWN_COMPROMISED_DEFAULT =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties p) {
        this.props = p;
        this.key = validateAndBuildKey(p.secret());
    }

    public static SecretKey validateAndBuildKey(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing key is missing. Set MEDTRACK_JWT_SECRET environment variable with a secure 512-bit (64+ character) key."
            );
        }
        String trimmed = rawSecret.trim();
        if (KNOWN_COMPROMISED_DEFAULT.equalsIgnoreCase(trimmed)
                || "change-me".equalsIgnoreCase(trimmed)
                || "changeme".equalsIgnoreCase(trimmed)
                || "secret".equalsIgnoreCase(trimmed)
                || "password".equalsIgnoreCase(trimmed)
                || "default".equalsIgnoreCase(trimmed)) {
            throw new IllegalStateException(
                    "Insecure JWT signing key: Default or compromised secret detected. Set a unique, cryptographically random key via MEDTRACK_JWT_SECRET."
            );
        }
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "Insecure JWT signing key: Secret must be at least " + MIN_KEY_BYTES + " bytes (512 bits) for HMAC-SHA512. Received " + bytes.length + " bytes."
            );
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issue(User u) {
        Instant now = Instant.now();
        List<String> permissions = u.getRole().getPermissions().stream().map(p -> p.getName()).sorted().toList();
        return Jwts.builder()
                .subject(u.getId().toString())
                .claim("email", u.getEmail())
                .claim("role", u.getRole().getName())
                .claim("warehouse_id", u.getAssignedWarehouse() == null ? null : u.getAssignedWarehouse().getId().toString())
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.accessTokenMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    public Claims claims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

        String alg = jws.getHeader().getAlgorithm();
        if (!EXPECTED_ALGORITHM.equalsIgnoreCase(alg)) {
            throw new UnsupportedJwtException("Unsupported JWT algorithm: " + alg + ". Only " + EXPECTED_ALGORITHM + " is permitted.");
        }
        return jws.getPayload();
    }

    public long expiry() {
        return props.accessTokenMinutes() * 60;
    }
}
