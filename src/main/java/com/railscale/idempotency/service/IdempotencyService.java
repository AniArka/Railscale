package com.railscale.idempotency.service;

import com.railscale.common.exception.ConflictException;
import com.railscale.idempotency.domain.IdempotencyKey;
import com.railscale.idempotency.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public record GateResult(
        boolean ownsProcessing,
        Integer cachedStatus,
        String cachedBody
    ) {
        public static GateResult newRequest() {
            return new GateResult(true, null, null);
        }

        public static GateResult replay(int status, String body) {
            return new GateResult(false, status, body);
        }
    }

    /**
     * Section 8 & Section 11: Atomic idempotency gate.
     * Uses REQUIRES_NEW propagation to commit the reservation lease independently of subsequent rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateResult enterGate(UUID userId, String idempotencyKey, String requestPayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return GateResult.newRequest();
        }

        String requestHash = hashPayload(requestPayload);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.DAYS);
        UUID recordId = UUID.randomUUID();

        int inserted = idempotencyKeyRepository.insertIfAbsent(
            recordId,
            userId,
            idempotencyKey,
            requestHash,
            now,
            expiresAt
        );

        if (inserted == 1) {
            return GateResult.newRequest();
        }

        // Key already exists: evaluate existing record
        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOpt.isEmpty()) {
            return GateResult.newRequest();
        }

        IdempotencyKey existing = existingOpt.get();
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED: Idempotency key reused with different request payload");
        }

        if (existing.getResponseStatus() == null) {
            throw new ConflictException("REQUEST_IN_PROGRESS: Request is currently in progress");
        }

        return GateResult.replay(existing.getResponseStatus(), existing.getResponseBody());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponse(UUID userId, String idempotencyKey, int status, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        idempotencyKeyRepository.storeResponse(userId, idempotencyKey, status, responseBody);
    }

    public String hashPayload(String payload) {
        if (payload == null) {
            payload = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
