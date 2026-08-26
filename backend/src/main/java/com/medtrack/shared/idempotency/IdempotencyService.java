package com.medtrack.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.inventory.entity.IdempotencyKey;
import com.medtrack.inventory.repository.IdempotencyKeyRepository;
import com.medtrack.shared.exception.ConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final IdempotencyKeyRepository keys;
    private final ObjectMapper json;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
    }

    public <T> T execute(UUID actorId, String key, String requestPath, Object requestPayload, Class<T> responseClass, Supplier<T> businessAction) {
        if (key == null || key.isBlank()) {
            return businessAction.get();
        }

        String fingerprint = fingerprint(requestPayload);
        keys.lock(actorId + ":" + requestPath + ":" + key);

        Optional<IdempotencyKey> existing = keys.findByKeyAndUserIdAndRequestPath(key, actorId, requestPath);
        if (existing.isPresent()) {
            IdempotencyKey prior = existing.get();
            if (!prior.getRequestFingerprint().equals(fingerprint)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used for a different request");
            }
            if (prior.getResponseBody() != null) {
                return read(prior.getResponseBody(), responseClass);
            }
            throw new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS", "Idempotency request is still processing");
        }

        IdempotencyKey record = keys.save(new IdempotencyKey(key, actorId, requestPath, fingerprint, Instant.now().plus(Duration.ofHours(24))));
        T response = businessAction.get();
        record.complete(200, write(response));
        keys.save(record);
        return response;
    }

    public String fingerprint(Object payload) {
        if (payload == null) return digest("{}");
        return digest(write(payload));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> T read(String body, Class<T> clazz) {
        try {
            return json.readValue(body, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}