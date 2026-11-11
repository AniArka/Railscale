package com.railscale.idempotency.repository;

import com.railscale.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

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
