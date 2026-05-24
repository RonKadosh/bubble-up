package com.ronkadosh.studybuddy.auth.persistence;

import com.ronkadosh.studybuddy.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByRotatedFromId(UUID rotatedFromId);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id in :ids and r.revokedAt is null")
    int revokeAll(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    // TODO iter 3: scheduled cleanup of rows where expiresAt < now() - 7 days
    // or revokedAt < now() - 7 days (keep recent revocations for reuse-detection forensics).
}
