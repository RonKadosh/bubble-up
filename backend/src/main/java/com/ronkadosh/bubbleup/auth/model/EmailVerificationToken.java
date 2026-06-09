package com.ronkadosh.bubbleup.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One-shot academic-email verification challenge.
 *
 * <p>Issued when a user signs in with a Google account whose email is NOT a
 * registered Israeli academic domain and wants to claim a uni identity via
 * a secondary {@code .ac.il} mailbox. We email a short URL containing a
 * fresh random token; clicking it within the TTL window verifies that the
 * user actually owns the secondary address.
 *
 * <p>Only the SHA-256 hash of the raw token lives in the DB — that way an
 * attacker who reads the table can't forge a valid link. {@code usedAt} is
 * set on successful redemption; rows are never re-used, even within the
 * TTL window.
 */
@Entity
@Table(
        name = "email_verification_tokens",
        indexes = {
                @Index(name = "idx_evt_user", columnList = "user_id"),
                @Index(name = "idx_evt_hash", columnList = "token_hash", unique = true)
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hex of the raw token mailed to the user. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** The academic address the user asked us to verify. */
    @Column(name = "requested_email", nullable = false, length = 320)
    private String requestedEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    @Setter
    private Instant usedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
