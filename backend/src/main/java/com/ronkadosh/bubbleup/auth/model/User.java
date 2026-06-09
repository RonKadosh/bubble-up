package com.ronkadosh.bubbleup.auth.model;

import com.ronkadosh.bubbleup.common.context.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Set only for legacy password-based accounts. {@code null} for every user
     * that signed in via Google OAuth (the post-v0.2 default). The column is
     * scheduled for removal once the migration is complete and no password
     * users remain. Until then it stays nullable to keep the column.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Stable Google subject identifier ({@code sub} claim of the ID token).
     * Unique per Google account; never reused. Used as the primary lookup key
     * for OAuth sign-ins so that a user changing their Google email still maps
     * to the same row.
     */
    @Column(name = "google_sub", unique = true, length = 64)
    @Setter
    private String googleSub;

    /**
     * True once we've confirmed the user actually owns this email. Always true
     * when the email itself was the Google account email (Google verifies it
     * for us). When the user signs in with a personal Google account but wants
     * to act as e.g. a BGU student, we send a confirmation link via SES — this
     * flag flips to true when they click it.
     */
    @Column(name = "email_verified", nullable = false)
    @Setter
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "suspended_until")
    @Setter
    private Instant suspendedUntil;

    @Column(name = "status_reason", length = 500)
    @Setter
    private String statusReason;

    @Column(name = "display_name", nullable = false, length = 100)
    @Setter
    private String displayName;

    @Column(length = 280)
    @Setter
    private String bio;

    @Column(name = "avatar_file_id", length = 64)
    @Setter
    private String avatarFileId;

    @Column(name = "avatar_content_type", length = 64)
    @Setter
    private String avatarContentType;

    @Column(name = "university_id")
    @Setter
    private UUID universityId;

    @Column(name = "department_id")
    @Setter
    private UUID departmentId;

    @Column(name = "enrollment_year")
    @Setter
    private Integer enrollmentYear;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    /**
     * Privileged email change used only after the user proves ownership of an
     * academic mailbox.
     */
    public void verifyEmail(String verifiedEmail) {
        this.email = verifiedEmail;
        this.emailVerified = true;
    }
}
