package com.railscale.idempotency.service;

import com.railscale.common.exception.ConflictException;
import com.railscale.idempotency.domain.IdempotencyKey;
import com.railscale.idempotency.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

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
     * Uses standard JPA save + unique constraint enforcement — works on PostgreSQL, H2, and all JPA-compliant databases.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateResult enterGate(UUID userId, String idempotencyKey, String requestPayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return GateResult.newRequest();
        }

        String requestHash = hashPayload(requestPayload);

        // Check if key already exists
        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);

        if (existingOpt.isPresent()) {
            return evaluateExistingKey(existingOpt.get(), requestHash);
        }

        // Try to insert new key — the UNIQUE(user_id, idempotency_key) constraint
        // guarantees atomicity against concurrent inserts on both PostgreSQL and H2.
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(1, ChronoUnit.DAYS);
            IdempotencyKey newKey = new IdempotencyKey(userId, idempotencyKey, requestHash, expiresAt);
            idempotencyKeyRepository.saveAndFlush(newKey);
            return GateResult.newRequest();
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted between our check and save.
            // Re-read and evaluate.
            log.debug("Idempotency key race condition detected for key: {}", idempotencyKey);
            Optional<IdempotencyKey> raceOpt = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (raceOpt.isPresent()) {
                return evaluateExistingKey(raceOpt.get(), requestHash);
            }
            // Should not happen, but treat as new
            return GateResult.newRequest();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponse(UUID userId, String idempotencyKey, int status, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        idempotencyKeyRepository.storeResponse(userId, idempotencyKey, status, responseBody);
    }

    private GateResult evaluateExistingKey(IdempotencyKey existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED: Idempotency key reused with different request payload");
        }
        if (existing.getResponseStatus() == null) {
            throw new ConflictException("REQUEST_IN_PROGRESS: Request is currently in progress");
        }
        return GateResult.replay(existing.getResponseStatus(), existing.getResponseBody());
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
