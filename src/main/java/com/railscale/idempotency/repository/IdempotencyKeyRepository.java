package com.railscale.idempotency.repository;

import com.railscale.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Section 8 & 11: Atomic concurrency gate using database unique constraint.
     * Returns 1 if key acquired by this request, 0 if key already exists.
     */
    @Modifying
    @Query(value = """
        INSERT INTO idempotency_keys (id, user_id, idempotency_key, request_hash, created_at, expires_at)
        VALUES (:id, :userId, :key, :hash, :createdAt, :expiresAt)
        ON CONFLICT (user_id, idempotency_key) DO NOTHING
    """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("key") String key,
        @Param("hash") String hash,
        @Param("createdAt") Instant createdAt,
        @Param("expiresAt") Instant expiresAt
    );

    @Modifying
    @Query("""
        UPDATE IdempotencyKey k
        SET k.responseStatus = :status, k.responseBody = :body
        WHERE k.userId = :userId AND k.idempotencyKey = :key
    """)
    int storeResponse(
        @Param("userId") UUID userId,
        @Param("key") String key,
        @Param("status") Integer status,
        @Param("body") String body
    );
}
