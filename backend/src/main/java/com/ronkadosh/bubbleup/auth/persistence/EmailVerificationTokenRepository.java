package com.ronkadosh.bubbleup.auth.persistence;

import com.ronkadosh.bubbleup.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Cancel every still-live token belonging to {@code userId}. Called when
     * a user requests a new verification email — we don't want the previous
     * link to keep working in parallel.
     */
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :now " +
            "where t.userId = :userId and t.usedAt is null and t.expiresAt > :now")
    int invalidateLiveTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Housekeeping: remove tokens past their TTL. */
    @Modifying
    @Query("delete from EmailVerificationToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /** Rate limit helper: how many requests has this user made recently? */
    @Query("select count(t) from EmailVerificationToken t " +
            "where t.userId = :userId and t.createdAt > :since")
    long countRecentRequests(@Param("userId") UUID userId, @Param("since") Instant since);
}
